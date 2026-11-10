import axiosClient from '../api/axiosClient';
import socketClient from '../api/socketClient';
import useRtcStore from '../store/rtcStore';
import { microphoneProblem } from './deviceCheck';
import { isInitiator, isPolite, MAX_PEERS, planConnections } from './meshPlan';
import { sessionIdOf, tuneOpusForMusic } from './sdp';
import { summarizeStats } from './stats';

/**
 * The audio mesh of a room (KAN-47, UR-4, FR-7): participants hear each other directly, browser to browser.
 *
 * One RTCPeerConnection per other participant (at most 4). The server only passes the negotiation messages along
 * (`/app/rooms/{roomId}/signal` → `/user/queue/signal`, KAN-45) and hands out the STUN/TURN servers
 * (`GET /api/rtc/ice-servers`, KAN-46); the audio never goes through it.
 *
 * - Connections open as soon as two participants have the room open, whether or not their microphones are on, so
 *   switching a microphone on only puts a track on a connection that already exists.
 * - Of two participants the one with the smaller user id starts the connection, so it starts with exactly one
 *   offer. After that both follow the "perfect negotiation" pattern: either may renegotiate (an ICE restart), and
 *   when two offers cross, the participant with the greater id withdraws theirs.
 * - The microphone is captured without echo cancellation, noise suppression and gain control, which are made for
 *   speech and mangle a guitar; hence the advice to use headphones. Opus is asked for stereo at about 128 kbps with
 *   short packets (`tuneOpusForMusic`).
 * - A connection that drops restarts ICE; one that fails, or does not come up in time, is opened anew by the
 *   participant who starts it. A participant who left the room is disconnected at once.
 * - In relay-only mode (the participant's setting) audio goes only through the TURN relay. Changing the setting
 *   replaces the connections, since a live one keeps its route.
 * - An offer of another session than the one a connection knows means the peer started over (a reloaded page, a
 *   changed setting): the connection is replaced by a new one that answers it.
 *
 * The microphone and the output are the ones chosen on the device check page (KAN-49), if any.
 *
 * Session descriptions and candidates hold network addresses, so they are never logged.
 *
 * Limits: only the participant who starts a connection opens it anew, so the other side waits for that. And a user
 * with the same room open in two browser tabs gets every signal in both, which breaks their audio: one tab per room.
 *
 * One room at a time: the room page calls startRtcMesh and the returned stop function when it leaves, and tells the
 * mesh who is in the room with setRoomParticipants.
 */

const MICROPHONE = {
  audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false, channelCount: 2 },
};
/** A dropped connection often comes back by itself; after this long ICE is restarted. */
const DISCONNECTED_GRACE_MS = 4000;
/** A connection that is not up after this long is opened anew. */
const CONNECT_TIMEOUT_MS = 15000;
const RETRY_DELAY_MS = 1500;
/** TURN credentials are asked for again this long before they expire. */
const CREDENTIAL_MARGIN_MS = 60000;

let mesh = null;

/**
 * The microphone chosen on the device check page, or the system's default one if none was chosen or the chosen one
 * is gone (an unplugged headset must not keep the participant silent).
 */
export async function openMicrophone(deviceId) {
  if (deviceId) {
    try {
      return await navigator.mediaDevices.getUserMedia(
        { audio: { ...MICROPHONE.audio, deviceId: { exact: deviceId } } });
    } catch (error) {
      if (!['OverconstrainedError', 'NotFoundError'].includes(error?.name)) throw error;
    }
  }
  return navigator.mediaDevices.getUserMedia(MICROPHONE);
}

/**
 * Starts the mesh for a room as the given user, stopping one that runs for another; returns the function that
 * stops it.
 */
export function startRtcMesh(roomId, myId) {
  mesh?.stop();
  const store = () => useRtcStore.getState();
  store().resetSession();

  const peers = new Map();
  let participants = [];
  let ice = null;
  let iceRequest = null;
  let localStream = null;
  let stopped = false;

  const send = (toId, type, payload) => socketClient.sendMessage(`/rooms/${roomId}/signal`,
    { type, toId, payload: JSON.stringify(payload) });

  /** The ICE servers, asked for again shortly before their credentials expire. */
  const iceServers = async () => {
    if (ice && ice.expiresAt - Date.now() > CREDENTIAL_MARGIN_MS) return ice.servers;
    iceRequest ??= axiosClient.get('/api/rtc/ice-servers')
      .then((response) => {
        // A STUN entry comes with `username: null`, which the browser would read as the name "null"
        const servers = response.data.iceServers.map((server) => Object.fromEntries(
          Object.entries(server).filter(([, value]) => value !== null)));
        ice = { servers, expiresAt: Date.parse(response.data.expiresAt) };
        return ice.servers;
      })
      .finally(() => {
        iceRequest = null;
      });
    return iceRequest;
  };

  const configuration = (servers) => ({
    iceServers: servers,
    iceTransportPolicy: store().relayOnly ? 'relay' : 'all',
  });

  const localTrack = () => localStream?.getAudioTracks()[0] ?? null;

  const isAlive = (peer) => ['new', 'connecting', 'connected'].includes(peer.pc.connectionState);

  const closePeer = (peerId) => {
    const peer = peers.get(peerId);
    if (!peer) return;
    peers.delete(peerId);
    clearTimeout(peer.graceTimer);
    clearTimeout(peer.connectTimer);
    peer.pc.onnegotiationneeded = null;
    peer.pc.onicecandidate = null;
    peer.pc.ontrack = null;
    peer.pc.onconnectionstatechange = null;
    peer.pc.close();
    if (peer.audio) {
      peer.audio.srcObject = null;
      peer.audio.remove();
    }
    store().removePeer(peerId);
  };

  /** Opens the connection anew, if this side is the one that starts it and the peer still has the room open. */
  const reopen = (peerId) => {
    closePeer(peerId);
    if (!stopped) setTimeout(reconcile, RETRY_DELAY_MS);
  };

  const play = (peer) => {
    peer.audio.play()
      .then(() => store().setSoundBlocked(false))
      .catch(() => {
        // Browsers refuse sound before the first click on the page
        if (peers.get(peer.id) === peer) store().setSoundBlocked(true);
      });
  };

  const createPeer = (peerId, servers) => {
    const pc = new RTCPeerConnection(configuration(servers));
    const peer = {
      id: peerId,
      pc,
      polite: isPolite(myId, peerId),
      makingOffer: false,
      ignoreOffer: false,
      remoteSessionId: null,
      sender: null,
      audio: null,
      // Signals are handled one after another, so a candidate never overtakes the description it belongs to
      queue: Promise.resolve(),
      graceTimer: null,
      connectTimer: null,
    };
    peers.set(peerId, peer);
    store().setPeer(peerId, { state: pc.connectionState });

    pc.onnegotiationneeded = async () => {
      try {
        peer.makingOffer = true;
        await pc.setLocalDescription();
        send(peerId, 'OFFER', pc.localDescription);
      } catch (error) {
        console.warn(`Could not make an offer to ${peerId}: ${error.name}`);
      } finally {
        peer.makingOffer = false;
      }
    };
    pc.onicecandidate = ({ candidate }) => {
      if (candidate) send(peerId, 'ICE_CANDIDATE', candidate);
    };
    pc.ontrack = ({ track }) => {
      if (!peer.audio) {
        peer.audio = new Audio();
        peer.audio.autoplay = true;
        // Chrome plays remote audio only from an element in the document
        peer.audio.hidden = true;
        document.body.append(peer.audio);
        // The output chosen on the device check page, where the browser can choose one
        const { outputDeviceId } = store();
        if (outputDeviceId) peer.audio.setSinkId?.(outputDeviceId).catch(() => {});
      }
      peer.audio.srcObject = new MediaStream([track]);
      play(peer);
    };
    pc.onconnectionstatechange = () => {
      if (peers.get(peerId) !== peer) return;
      const state = pc.connectionState;
      store().setPeer(peerId, { state });
      clearTimeout(peer.graceTimer);
      if (state === 'connected') {
        clearTimeout(peer.connectTimer);
      } else if (state === 'disconnected') {
        peer.graceTimer = setTimeout(() => {
          if (pc.connectionState === 'disconnected') pc.restartIce();
        }, DISCONNECTED_GRACE_MS);
        // A peer who closed the room goes offline first and is not coming back on this connection
        reconcile();
      } else if (state === 'failed') {
        if (isInitiator(myId, peerId)) {
          reopen(peerId);
        } else {
          reconcile();
        }
      }
    };
    peer.connectTimer = setTimeout(() => {
      if (peers.get(peerId) === peer && pc.connectionState !== 'connected' && isInitiator(myId, peerId)) {
        reopen(peerId);
      }
    }, CONNECT_TIMEOUT_MS);
    return peer;
  };

  /** The one audio line of the connection carries this side's microphone whenever it is on. */
  const carryMicrophone = (peer, transceiver) => {
    transceiver.direction = 'sendrecv';
    peer.sender = transceiver.sender;
    return peer.sender.replaceTrack(localTrack());
  };

  const call = async (peerId) => {
    const servers = await iceServers();
    if (stopped || peers.has(peerId) || peers.size >= MAX_PEERS) return;
    const peer = createPeer(peerId, servers);
    // Adding the line makes the connection need a negotiation, which sends the offer
    await carryMicrophone(peer, peer.pc.addTransceiver('audio', { direction: 'sendrecv' }));
  };

  /** Opens and closes connections for who is in the room now. */
  function reconcile() {
    // Without the server connection an offer would go nowhere; connecting reconciles again
    if (stopped || !socketClient.isConnected()) return;
    const connections = [...peers.values()].map((peer) => ({ userId: peer.id, alive: isAlive(peer) }));
    const { open, close } = planConnections(myId, participants, connections);
    close.forEach(closePeer);
    open.forEach((peerId) => call(peerId).catch((error) => {
      // E.g. the ICE servers could not be fetched; nothing else would try again in a quiet room
      console.warn(`Could not call ${peerId}: ${error.name}`);
      setTimeout(reconcile, RETRY_DELAY_MS);
    }));
  }

  const handleDescription = async (fromId, type, description) => {
    let peer = peers.get(fromId);
    const sessionId = sessionIdOf(description.sdp);
    if (type === 'OFFER' && peer?.remoteSessionId && sessionId !== peer.remoteSessionId) {
      // The peer started over (reloaded the page), so the connection it knew is gone
      closePeer(fromId);
      peer = null;
    }
    if (!peer) {
      const inRoom = participants.some((participant) => participant.userId === fromId);
      if (type !== 'OFFER' || !inRoom || peers.size >= MAX_PEERS) return;
      const servers = await iceServers();
      if (stopped || peers.has(fromId)) return;
      peer = createPeer(fromId, servers);
    }
    const { pc } = peer;
    const collision = type === 'OFFER' && (peer.makingOffer || pc.signalingState !== 'stable');
    peer.ignoreOffer = !peer.polite && collision;
    if (peer.ignoreOffer) return;

    await pc.setRemoteDescription({ type: description.type, sdp: tuneOpusForMusic(description.sdp) });
    peer.remoteSessionId = sessionId;
    if (type === 'OFFER') {
      // The audio line the peer offered. If this side had offered one of its own at the same moment, that offer
      // was withdrawn for the peer's, and its line, now unused, is dropped
      const offered = pc.getTransceivers()
        .find((transceiver) => transceiver.mid !== null && transceiver.receiver.track?.kind === 'audio');
      if (offered && offered.sender !== peer.sender) {
        pc.getTransceivers()
          .filter((transceiver) => transceiver !== offered && transceiver.mid === null)
          .forEach((transceiver) => transceiver.stop());
        await carryMicrophone(peer, offered);
      }
      await pc.setLocalDescription();
      send(fromId, 'ANSWER', pc.localDescription);
    }
  };

  const handleSignal = async (signal) => {
    if (stopped || signal.roomId !== roomId || signal.fromId === myId) return;
    let payload;
    try {
      payload = JSON.parse(signal.payload);
    } catch {
      return;
    }
    if (signal.type === 'OFFER' || signal.type === 'ANSWER') {
      await handleDescription(signal.fromId, signal.type, payload);
    } else if (signal.type === 'ICE_CANDIDATE') {
      const peer = peers.get(signal.fromId);
      if (!peer) return;
      try {
        await peer.pc.addIceCandidate(payload);
      } catch (error) {
        // Candidates of an offer this side ignored, or of a connection that was replaced meanwhile
        if (!peer.ignoreOffer) console.debug(`Dropped a candidate from ${signal.fromId}: ${error.name}`);
      }
    }
  };

  // One queue per sender keeps a peer's signals in order; a failure must not block the ones after it
  const queues = new Map();
  const onSignal = (signal) => {
    const previous = queues.get(signal.fromId) ?? Promise.resolve();
    queues.set(signal.fromId, previous
      .then(() => handleSignal(signal))
      .catch((error) => console.warn(`Could not handle ${signal.type} from ${signal.fromId}: ${error.name}`)));
  };

  let subscription = null;
  const stopListening = socketClient.onConnect(() => {
    // A new connection starts without subscriptions; the audio connections themselves outlive it
    subscription = socketClient.subscribe('/user/queue/signal', onSignal);
    reconcile();
  });

  // Changing the relay-only setting replaces the connections: a live connection keeps the route it has, whatever
  // its configuration is changed to. Either side may do this; the peer sees an offer of a new session and answers
  // it with a new connection of its own
  const stopWatchingSettings = useRtcStore.subscribe((state, previous) => {
    if (state.relayOnly === previous.relayOnly) return;
    [...peers.keys()].forEach((peerId) => {
      closePeer(peerId);
      call(peerId).catch((error) => console.warn(`Could not call ${peerId}: ${error.name}`));
    });
  });

  const releaseMicrophone = () => {
    localStream?.getTracks().forEach((track) => {
      track.onended = null;
      track.stop();
    });
    localStream = null;
    peers.forEach((peer) => peer.sender?.replaceTrack(null).catch(() => {}));
  };

  const current = {
    setParticipants: (list) => {
      participants = list;
      reconcile();
    },
    enableMicrophone: async () => {
      if (localStream || store().micState === 'starting') return;
      store().setMic('starting');
      try {
        const stream = await openMicrophone(store().inputDeviceId);
        if (stopped) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }
        localStream = stream;
        const track = localTrack();
        // The microphone was unplugged, or the permission taken back
        track.onended = () => {
          releaseMicrophone();
          store().setMic('error', 'The microphone stopped working.');
        };
        // A connection closed meanwhile refuses the track; that is no reason to refuse the microphone
        await Promise.all([...peers.values()].map((peer) => peer.sender?.replaceTrack(track).catch(() => {})));
        store().setMic('on');
        current.resumeSound();
      } catch (error) {
        // Never leave a microphone running that the page shows as off
        releaseMicrophone();
        store().setMic('error', microphoneProblem(error?.name).message);
      }
    },
    disableMicrophone: () => {
      releaseMicrophone();
      store().setMic('off');
    },
    resumeSound: () => peers.forEach((peer) => peer.audio && play(peer)),
    stats: async () => Promise.all([...peers.values()].map(async (peer) => ({
      userId: peer.id,
      state: peer.pc.connectionState,
      ...summarizeStats([...(await peer.pc.getStats()).values()]),
    }))),
    stop: () => {
      if (mesh !== current) return;
      mesh = null;
      stopped = true;
      stopListening();
      stopWatchingSettings();
      subscription?.unsubscribe();
      [...peers.keys()].forEach(closePeer);
      releaseMicrophone();
      store().resetSession();
    },
  };
  mesh = current;
  return current.stop;
}

/**
 * Tells the mesh who is in the room: the participants as `{ userId, online }`. Connections to those who left are
 * closed, and the online ones this user is to call are called.
 */
export function setRoomParticipants(participants) {
  mesh?.setParticipants(participants.map(({ userId, online }) => ({ userId, online: Boolean(online) })));
}

/** Asks for the microphone and sends it to everyone connected; the outcome is in the rtc store's `micState`. */
export function enableMicrophone() {
  return mesh?.enableMicrophone();
}

export function disableMicrophone() {
  mesh?.disableMicrophone();
}

/** Starts the others' audio after the browser refused it; call it from a click. */
export function resumeSound() {
  mesh?.resumeSound();
}

/**
 * The statistics of every connection, as `{ userId, state, ...summarizeStats }`; an empty list while no mesh runs.
 */
export function getRtcStats() {
  return mesh ? mesh.stats() : Promise.resolve([]);
}

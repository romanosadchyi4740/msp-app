import { useCallback, useEffect, useRef } from 'react';
import toast from 'react-hot-toast';
import axiosClient from '../api/axiosClient';
import socketClient from '../api/socketClient';
import usePlayerStore, { broadcastSyncDestination, nextBroadcastSequence } from '../store/playerStore';
import { serverNow, startClockSync, stopClockSync } from './clockSync';
import { expectedPosition, HOST_REANCHOR_MS, hostPlayback, isNewer, seekTarget, shouldPlay } from './broadcastState';

const CORRECTION_INTERVAL_MS = 1000;
const SYNC_RETRY_MS = 1500;
const SYNC_RETRIES = 5;
const TRACK_RETRY_MS = 3000;

function unsubscribeAll(subscriptions) {
  for (const subscription of subscriptions.splice(0)) {
    try {
      subscription?.unsubscribe();
    } catch {
      // The connection that carried it is gone already
    }
  }
}

/**
 * Listening broadcasts with the state kept on the server (KAN-109). The host reports its track, position and play
 * state (RESUME on every reconnect, UPDATE on every change, and again every HOST_REANCHOR_MS while playing). A listener
 * applies the server's states in version order (snapshot on every connect, then the topic), loads a new track
 * without playing it, and once a second moves its player to where the host is and plays or pauses it to match.
 *
 * Returns the handlers for the <audio> element's play, pause and seeked events, which report the host's changes.
 */
export default function useBroadcast(audioRef) {
  const status = usePlayerStore((state) => state.broadcastStatus);
  const broadcastId = usePlayerStore((state) => state.activeBroadcastId);
  const chatId = usePlayerStore((state) => state.activeBroadcastChatId);
  const trackId = usePlayerStore((state) => state.currentTrack?.id);
  const targetRef = useRef(null);

  const sendHostState = useCallback((command = 'UPDATE') => {
    const store = usePlayerStore.getState();
    if (store.broadcastStatus !== 'MASTER' || !store.activeBroadcastId) return;
    socketClient.sendMessage(broadcastSyncDestination(store.activeBroadcastChatId, store.activeBroadcastId), {
      command,
      ...hostPlayback(store.currentTrack?.id, audioRef.current),
      sequence: nextBroadcastSequence(),
    });
  }, [audioRef]);

  // Refusals of either role arrive on the user's own error queue
  useEffect(() => {
    if (status === 'IDLE' || !broadcastId) return undefined;
    const subscriptions = [];
    const stop = socketClient.onConnect(() => {
      unsubscribeAll(subscriptions);
      subscriptions.push(socketClient.subscribe('/user/queue/broadcast-errors', (error) => {
        if (usePlayerStore.getState().broadcastStatus === 'MASTER' && String(error?.code).startsWith('409')) {
          // Ended while this host was away too long; the server announced it in the chat already
          usePlayerStore.setState({ broadcastStatus: 'IDLE', activeBroadcastId: null, activeBroadcastChatId: null });
          toast.error('Your broadcast ended while you were offline');
          return;
        }
        toast.error(error?.message || 'The broadcast refused that');
      }));
    });
    return () => {
      stop();
      unsubscribeAll(subscriptions);
    };
  }, [status, broadcastId]);

  // Host: every reconnect continues the broadcast from the new session (RESUME, which the server refuses for one it
  // ended or forgot), or sends the START that could not go out; re-anchor while playing
  useEffect(() => {
    if (status !== 'MASTER' || !broadcastId) return undefined;
    // The current connection carried the START already
    let skipCurrent = socketClient.isConnected() && !usePlayerStore.getState().broadcastStartPending;
    const stop = socketClient.onConnect(() => {
      if (skipCurrent) {
        skipCurrent = false;
        return;
      }
      if (usePlayerStore.getState().broadcastStartPending) {
        usePlayerStore.setState({ broadcastStartPending: false });
        sendHostState('START');
      } else {
        sendHostState('RESUME');
      }
    });
    const timer = setInterval(() => {
      if (audioRef.current && !audioRef.current.paused) sendHostState();
    }, HOST_REANCHOR_MS);
    return () => {
      stop();
      clearInterval(timer);
    };
  }, [status, broadcastId, sendHostState, audioRef]);

  // Host: a new track (it starts paused at 0; its play event follows). Only on a real change, not when the
  // broadcast starts: START already carries the state, and an UPDATE right behind it could overtake it
  const lastTrackRef = useRef(trackId);
  useEffect(() => {
    if (lastTrackRef.current === trackId) return;
    lastTrackRef.current = trackId;
    if (status === 'MASTER') sendHostState();
  }, [trackId, status, sendHostState]);

  // Listener
  useEffect(() => {
    if (status !== 'SYNC' || !broadcastId || !chatId) return undefined;
    let cancelled = false;
    let lastVersion = null;
    let pending = Promise.resolve();
    const subscriptions = [];
    targetRef.current = null;
    startClockSync();

    // Loads the host's track paused; the correction plays it. A failed load (network, a deleted track, the stream
    // URL) is tried again by the correction after TRACK_RETRY_MS, since a paused host sends no new state
    let loadingTrackId = null;
    let lastLoadFailedAt = 0;
    const loadTrack = async (state) => {
      if (loadingTrackId === state.trackId) return;
      loadingTrackId = state.trackId;
      try {
        const { data: track } = await axiosClient.get(`/api/tracks/${state.trackId}`);
        if (cancelled || targetRef.current?.trackId !== state.trackId) return;
        await usePlayerStore.getState().playTrack(track, [], expectedPosition(targetRef.current, serverNow()),
          { autoplay: false, fromBroadcast: true });
        if (usePlayerStore.getState().currentTrack?.id !== state.trackId) lastLoadFailedAt = Date.now();
      } catch (error) {
        lastLoadFailedAt = Date.now();
        console.warn(`Could not load the host's track: ${error?.name}`);
      } finally {
        loadingTrackId = null;
      }
    };

    const correct = () => {
      const state = targetRef.current;
      const audio = audioRef.current;
      if (cancelled || !state || state.status !== 'ACTIVE') return;
      const store = usePlayerStore.getState();
      if (state.trackId && store.currentTrack?.id !== state.trackId) {
        if (!loadingTrackId && Date.now() - lastLoadFailedAt >= TRACK_RETRY_MS) loadTrack(state);
        return;
      }
      const want = shouldPlay(state, store.broadcastHeld);
      if (state.trackId && !store.broadcastHeld && audio && audio.readyState >= 1) {
        const target = seekTarget(audio.currentTime, expectedPosition(state, serverNow()), audio.duration);
        if (target !== null) audio.currentTime = target;
      }
      if (store.isPlaying !== want) store.setIsPlaying(want);
    };

    const apply = async (state) => {
      if (cancelled || !isNewer(state, lastVersion)) return;
      if (state.status === 'TERMINATED') {
        cancelled = true;
        usePlayerStore.getState().leaveBroadcast();
        usePlayerStore.getState().setIsPlaying(false);
        toast('The broadcast has ended');
        return;
      }
      lastVersion = state.version;
      targetRef.current = state;
      if (state.trackId && state.trackId !== usePlayerStore.getState().currentTrack?.id) {
        await loadTrack(state);
      }
      correct();
    };

    // One state at a time, in arrival order: a pause that arrives while a track loads waits for the load
    const enqueue = (state) => {
      pending = pending.then(() => apply(state))
        .catch((error) => console.warn(`Could not apply the broadcast state: ${error?.name}`));
    };

    // The server handles a session's messages in parallel, so the request can overtake the subscription its
    // answer needs; ask again until a state has arrived
    let syncRetry = null;
    const requestSync = (attempt = 0) => {
      clearTimeout(syncRetry);
      if (cancelled) return;
      socketClient.sendMessage(broadcastSyncDestination(chatId, broadcastId), { command: 'REQUEST_SYNC' });
      if (attempt < SYNC_RETRIES) {
        syncRetry = setTimeout(() => {
          if (lastVersion === null) requestSync(attempt + 1);
        }, SYNC_RETRY_MS);
      }
    };

    const stop = socketClient.onConnect(() => {
      unsubscribeAll(subscriptions);
      // The snapshot of this connection decides; versions do not carry over if the server restarted meanwhile
      lastVersion = null;
      subscriptions.push(socketClient.subscribe(`/topic/broadcast/${chatId}/${broadcastId}`, enqueue));
      subscriptions.push(socketClient.subscribe(`/user/queue/broadcast/${chatId}/${broadcastId}/snapshot`, enqueue));
      requestSync();
    });
    const timer = setInterval(correct, CORRECTION_INTERVAL_MS);

    return () => {
      cancelled = true;
      stop();
      unsubscribeAll(subscriptions);
      clearInterval(timer);
      clearTimeout(syncRetry);
      stopClockSync();
      targetRef.current = null;
    };
  }, [status, broadcastId, chatId, audioRef]);

  const onHostChange = useCallback(() => sendHostState(), [sendHostState]);
  return { onPlay: onHostChange, onPause: onHostChange, onSeeked: onHostChange };
}

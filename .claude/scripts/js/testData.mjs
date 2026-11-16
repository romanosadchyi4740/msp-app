// Throwaway test data for browser checks, through the app's own REST API and STOMP (seed-test-data skill).
// Import it from a short seed script in the scratchpad:
//
//   import { createSession } from 'D:/.../music-app/.claude/scripts/js/testData.mjs';
//   const data = createSession(new URL('./seed.json', import.meta.url));
//   const host = await data.register('Check host');
//   ...
//
// Every user's credentials are written to the state file before the account is created, so
// `node testData.mjs cleanup <state file>` can sign in and delete them even when a later step fails or the token
// has expired. Deleting a user (DELETE /api/users/me) runs every
// UserDataCleanup, which removes their rooms, tabs, tracks, albums, playlists and chats.
import { readFileSync, writeFileSync, existsSync, unlinkSync } from 'node:fs';
import { randomBytes } from 'node:crypto';
import { fileURLToPath, pathToFileURL } from 'node:url';

export const API = process.env.TEST_API_URL ?? 'http://localhost:8080';
const STOMP_URL = API.replace(/^http/, 'ws') + '/ws-raw';
const STOMP_MODULE = new URL('../../../frontend/node_modules/@stomp/stompjs/esm6/index.js', import.meta.url);

async function call(method, path, token, body) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  let payload;
  if (body instanceof FormData) payload = body;
  else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  const response = await fetch(API + path, { method, headers, body: payload });
  if (!response.ok) throw new Error(`${method} ${path}: ${response.status} ${await response.text()}`);
  const text = await response.text();
  return { response, body: text ? (() => { try { return JSON.parse(text); } catch { return text; } })() : null };
}

/**
 * A short, valid mono 16-bit WAV (a sine tone with a little random noise), so uploads pass the server's audio
 * checks. The noise makes every file unique: the server rejects audio it already has with 409, including tracks
 * left over from earlier runs.
 */
export function wav(frequency = 440, seconds = 1) {
  const rate = 8000;
  const samples = rate * seconds;
  const noise = randomBytes(samples);
  const buffer = Buffer.alloc(44 + samples * 2);
  buffer.write('RIFF', 0); buffer.writeUInt32LE(36 + samples * 2, 4); buffer.write('WAVE', 8);
  buffer.write('fmt ', 12); buffer.writeUInt32LE(16, 16); buffer.writeUInt16LE(1, 20); buffer.writeUInt16LE(1, 22);
  buffer.writeUInt32LE(rate, 24); buffer.writeUInt32LE(rate * 2, 28); buffer.writeUInt16LE(2, 32);
  buffer.writeUInt16LE(16, 34); buffer.write('data', 36); buffer.writeUInt32LE(samples * 2, 40);
  for (let i = 0; i < samples; i++) {
    const tone = 8000 * Math.sin((2 * Math.PI * frequency * i) / rate);
    buffer.writeInt16LE(Math.round(tone + noise[i] - 128), 44 + i * 2);
  }
  return buffer;
}

export function createSession(stateFile) {
  const state = existsSync(stateFile) ? JSON.parse(readFileSync(stateFile, 'utf8')) : { users: {} };
  const save = () => writeFileSync(stateFile, JSON.stringify(state, null, 2));

  return {
    state,
    save,

    /**
     * Registers a user; `user.token` is what the API and STOMP take. The credentials are saved before the request,
     * so even a crash right after it leaves an account that cleanup can still sign in to and delete.
     */
    async register(displayName, key = displayName) {
      const email = `check-${randomBytes(6).toString('hex')}@example.test`;
      const password = randomBytes(12).toString('hex');
      const user = { email, password };
      state.users[key] = user;
      save();
      const registered = (await call('POST', '/auth/register', null, { email, password, displayName })).body;
      user.token = registered.accessToken;
      user.id = registered.userId;
      save();
      return user;
    },

    /** The role is a JWT claim, so the response carries a new token in the Authorization header. */
    async becomeArtist(user) {
      const { response } = await call('POST', '/api/users/me/become-artist', user.token);
      user.token = (response.headers.get('Authorization') ?? '').replace(/^Bearer /, '') || user.token;
      save();
      return user;
    },

    async album(artist, title = 'Check album') {
      return (await call('POST', '/api/albums', artist.token, { title, description: '', coverUrl: null })).body;
    },

    /**
     * Needs an artist and an album (every track belongs to one) and 1 to 4 genre ids. The audio is unique each time
     * (see wav()). POST /api/tracks answers without a body, so the track is read back through the search.
     */
    async track(artist, albumId, title = 'Check track', frequency = 440) {
      const genres = (await call('GET', '/api/genres', artist.token)).body;
      const form = new FormData();
      form.append('albumId', albumId);
      form.append('title', title);
      form.append('duration', '1');
      form.append('genreIds', (Array.isArray(genres) ? genres : genres.content)[0].id);
      form.append('file', new Blob([wav(frequency)], { type: 'audio/wav' }), `${title}.wav`);
      await call('POST', '/api/tracks', artist.token, form);
      const query = `query=${encodeURIComponent(title)}&userId=${artist.id}&pageNo=0&pageSize=20`;
      const found = (await call('GET', `/api/tracks/search?${query}`, artist.token)).body;
      return (Array.isArray(found) ? found : found.content).find((t) => t.title === title);
    },

    async existingTracks(user, count = 3) {
      return (await call('GET', `/api/tracks?pageNo=0&pageSize=${count}`, user.token)).body.content;
    },

    async like(user, trackId) {
      await call('POST', `/api/tracks/${trackId}/like`, user.token);
    },

    async playlist(user, title = 'Check playlist', trackIds = []) {
      const playlist = (await call('POST', '/api/playlists', user.token,
        { title, description: '', isPublic: false })).body;
      for (const trackId of trackIds) await call('POST', `/api/playlists/${playlist.id}/tracks/${trackId}`, user.token);
      return playlist;
    },

    async directChat(user, other) {
      return (await call('POST', `/api/chats/with/${other.id}`, user.token)).body;
    },

    /** A group chat ("cluster"); the invited members accept, so they can read and write. */
    async groupChat(owner, members, groupName = 'Check group') {
      const chat = (await call('POST', '/api/chats/cluster', owner.token,
        { groupName, participantIds: members.map((m) => m.id) })).body;
      for (const member of members) {
        try { await call('PUT', `/api/chats/${chat.id}/accept`, member.token); } catch { /* already a member */ }
      }
      return chat;
    },

    /**
     * Sends chat messages over STOMP as the user (the app has no REST endpoint for sending). `content` is
     * required by the database: a voice message sends `content: ''` with its `fileUrl`, as the client does.
     */
    async chatMessages(user, messages) {
      const { Client } = await import(STOMP_MODULE.href);
      await new Promise((resolve, reject) => {
        const client = new Client({
          brokerURL: STOMP_URL,
          connectHeaders: { Authorization: `Bearer ${user.token}` },
          reconnectDelay: 0,
          onConnect: () => {
            for (const message of messages) {
              client.publish({ destination: '/app/chat', body: JSON.stringify({ content: '', ...message }) });
            }
            setTimeout(() => client.deactivate().then(resolve), 1500);
          },
          onStompError: (frame) => reject(new Error(frame.headers.message)),
          onWebSocketError: () => reject(new Error(`WebSocket error on ${STOMP_URL}`)),
        });
        client.activate();
      });
    },

    /** Uploads a WAV as a chat attachment and returns the fileUrl a voice message carries. */
    async voiceAttachment(user, frequency = 440) {
      const form = new FormData();
      form.append('file', new Blob([wav(frequency)], { type: 'audio/wav' }), 'voice.wav');
      return (await call('POST', '/api/messages/upload', user.token, form)).body;
    },

    async asciiTab(user, title = 'Check tab') {
      const rawText = ['e', 'B', 'G', 'D', 'A', 'E'].map((s) => `${s}|${'-0-0-0-0-|'.repeat(8)}`).join('\n');
      return (await call('POST', '/api/tabs', user.token, { title, artist: 'E2E', tempoBpm: 100, timeSignature: '4/4',
        isPublic: false, rightsConfirmed: true, rawText })).body;
    },

    async room(host, name = 'Check room') {
      return (await call('POST', '/api/rooms', host.token, { name })).body;
    },

    async join(user, inviteCode) {
      return (await call('POST', `/api/rooms/join/${encodeURIComponent(inviteCode)}`, user.token)).body;
    },

    async startLesson(host, roomId, tabId) {
      return (await call('POST', `/api/rooms/${roomId}/sessions`, host.token, { tabId })).body;
    },
  };
}

/** A fresh token from the stored credentials, for a token that expired (24 h) or was never stored. */
async function signIn(user) {
  const login = (await call('POST', '/auth/login', null, { email: user.email, password: user.password })).body;
  return login.accessToken;
}

/**
 * Deletes every user in the state file. Users that could not be deleted stay in the file, and the result is false;
 * the file is removed only when all are gone.
 */
export async function cleanup(stateFile) {
  const state = JSON.parse(readFileSync(stateFile, 'utf8'));
  const left = {};
  for (const [key, user] of Object.entries(state.users)) {
    try {
      try {
        if (!user.token) throw new Error('401 no token yet');
        await call('DELETE', '/api/users/me', user.token);
      } catch (error) {
        if (!/: 401 |401 no token/.test(error.message)) throw error;
        await call('DELETE', '/api/users/me', await signIn(user));
      }
      console.log(`deleted ${key}`);
    } catch (error) {
      console.log(`could not delete ${key} (${user.email}): ${error.message}`);
      left[key] = user;
    }
  }
  if (Object.keys(left).length) {
    writeFileSync(stateFile, JSON.stringify({ ...state, users: left }, null, 2));
    return false;
  }
  unlinkSync(stateFile);
  return true;
}

// node testData.mjs cleanup <state file>
if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  const [command, file] = process.argv.slice(2);
  if (command !== 'cleanup' || !file) {
    console.error('usage: node testData.mjs cleanup <state file>');
    process.exit(2);
  }
  process.exit((await cleanup(file)) ? 0 : 1);
}

export const scriptPath = fileURLToPath(import.meta.url);

---
name: seed-test-data
description: Create throwaway users and data (artists, albums, tracks, likes, playlists, direct and group chats with text, shared-track and voice messages, tabs, rooms, lessons) through the msp-app / SyncChord REST API and STOMP for a browser check or e2e run, and delete them afterwards, with the helper module .claude/scripts/js/testData.mjs. Use when a page must be checked with data on it, when an acceptance criterion needs a second user, a chat, an upload or a lesson, or when the owner asks to set up test data.
---

# Throwaway test data

Never use or change the owner's accounts or data. Everything here is created through the app's own API, so the
local database stays consistent, and removed by deleting the throwaway accounts (`DELETE /api/users/me` runs every
`UserDataCleanup`: rooms, tabs, tracks, albums, playlists, chats). Needs the backend running (`browser-check`).

## 1. Write a seed script in the scratchpad
```js
// <scratchpad>/seed/seed.mjs
import { createSession } from 'file:///D:/University/SWE_Bachelor/term8/Diploma/MusicSocialApp/music-app/.claude/scripts/js/testData.mjs';

const data = createSession(new URL('./seed.json', import.meta.url));
const artist = await data.becomeArtist(await data.register('Check artist', 'artist'));
const fan = await data.register('Check fan', 'fan');
const album = await data.album(artist);
const track = await data.track(artist, album.id, 'Check track');
for (const t of await data.existingTracks(fan, 3)) await data.like(fan, t.id);
const group = await data.groupChat(artist, [fan], 'Check group');
await data.chatMessages(fan, [{ chatId: group.id, content: 'hello' },
  { chatId: group.id, sharedTrackId: track.id, sharedTrackTimestamp: 42 }]);
await data.chatMessages(artist, [{ chatId: group.id, fileUrl: await data.voiceAttachment(artist) }]);
const room = await data.room(artist);
await data.join(fan, room.inviteCode);
await data.startLesson(artist, room.id, (await data.asciiTab(artist)).id);
console.log(JSON.stringify({ fan: fan.email, room: room.id }));
```
Run it with `node seed.mjs` from that folder. Sign in through the login page with an account from `seed.json`
(email and password are there; do not print passwords in chat).

## 2. Clean up
`node .claude/scripts/js/testData.mjs cleanup <scratchpad>/seed/seed.json` deletes every user in the state file and
then the file. If any user could not be deleted it keeps them in the file, prints why and exits with 1: fix the
cause and run it again, and never delete a state file that still lists users. Then delete the seed folder. Check a row count in Postgres if in doubt
(`docker exec music_app_postgres psql -U postgres -d music_app_db -tAc "select count(*) from ..."`).
Uploaded files of a failed message or upload may stay in MinIO unattached; say so in the report.

## 3. What the API does (learnt the hard way)
- Each user's credentials are saved to the state file before the account is created, so nothing can fail in between. A script that keeps
  credentials only in memory leaves undeletable accounts behind when a later step throws (two "Check X/Y" accounts
  were stranded that way in KAN-98).
- `POST /api/users/me/become-artist` returns a new JWT in the `Authorization` response header (the role is a claim);
  the old token is still a plain user.
- A track needs an album and 1 to 4 genre ids. Identical audio is rejected with 409 (also against tracks left over
  from earlier runs), so `wav()` adds random noise to every file. `POST /api/tracks` answers without a body
  (`void`, 201): the helper reads the track back through the track search filtered by artist.
- `/auth/register` and `/auth/login` return `accessToken` and `userId`; `GET /api/users/me` returns `id`.
- Tokens expire after 24 hours; `cleanup` signs in again with the stored password when a delete gets 401, and keeps
  the users it could not delete in the state file (exit code 1) instead of dropping them.
- Chat messages exist only over STOMP (`/app/chat`); the helper connects to `/ws-raw` with the frontend's
  `@stomp/stompjs`. The `messages.content` column is NOT NULL: voice and shared-track messages send `content: ''`,
  as the client does, and a refused message only shows up as an error in the backend log.
- Group chats are "clusters"; invited members accept with `PUT /api/chats/{id}/accept`.
- File inputs in the page: use Claude in Chrome's `file_upload` tool with a file in the scratchpad (e.g. a WAV from
  `wav()` written to disk). Never click a file input: the native dialog blocks the extension.
- Extend `testData.mjs` when a new kind of data is needed, keep each helper to the app's public API, and note any
  surprise in this section.

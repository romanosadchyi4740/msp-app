import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { roomHints } from './firstRoomHints.js';

describe('roomHints', () => {
  test('tells the host how to invite and start a lesson', () => {
    const ids = roomHints({ isHost: true, roomPath: '/rooms/1' }).map((hint) => hint.id);
    assert.deepEqual(ids, ['invite', 'lesson', 'audio']);
  });

  test('tells a participant that the host leads', () => {
    const ids = roomHints({ isHost: false, roomPath: '/rooms/1' }).map((hint) => hint.id);
    assert.deepEqual(ids, ['follow', 'audio', 'chat']);
  });

  test('links the device check back to the room', () => {
    const audio = roomHints({ isHost: false, roomPath: '/rooms/a b' }).find((hint) => hint.id === 'audio');
    assert.equal(audio.link.to, '/device-check?next=%2Frooms%2Fa%20b');
  });

  test('every hint has a title and a text', () => {
    for (const isHost of [true, false]) {
      for (const hint of roomHints({ isHost, roomPath: '/rooms/1' })) {
        assert.ok(hint.title && hint.text, hint.id);
      }
    }
  });
});

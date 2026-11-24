import { beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import usePlaybackStore from './playbackStore.js';

const ROOM = 'room-1';

const T0 = 1_700_000_000_000;

function state(version, overrides = {}) {
  return { sessionId: 'lesson-1', measure: 1, beat: 0, status: 'PLAYING', version, serverTime: T0 + version,
    ...overrides };
}

const store = () => usePlaybackStore.getState();

describe('playbackStore', () => {
  beforeEach(() => store().reset(ROOM));

  test('applies newer states and drops stale versions', () => {
    assert.equal(store().applyState(state(2)), true);
    assert.equal(store().applyState(state(1, { measure: 9 })), false);
    assert.equal(store().applyState(state(2, { measure: 9 })), false);

    assert.equal(store().playback.measure, 1);
    assert.equal(store().applyState(state(3, { measure: 4 })), true);
    assert.equal(store().playback.measure, 4);
  });

  test('a snapshot older than the applied state changes nothing', () => {
    store().applyState(state(5, { status: 'PAUSED' }));

    assert.equal(store().applySnapshot({ roomId: ROOM, playback: state(4) }), false);
    assert.equal(store().playback.status, 'PAUSED');
  });

  test('a snapshot catches up a joiner', () => {
    assert.equal(store().applySnapshot({ roomId: ROOM, sessionId: 'lesson-1', playback: state(7) }), true);
    assert.equal(store().playback.version, 7);
  });

  test('a snapshot of a room without a lesson clears the playback', () => {
    store().applyState(state(1));

    assert.equal(store().applySnapshot({ roomId: ROOM, sessionId: null, playback: null, serverTime: T0 + 10 }), true);
    assert.equal(store().playback, null);
  });

  test('a snapshot without a lesson taken before a lesson started keeps the lesson', () => {
    // The lesson started (and its state arrived) while the snapshot was on its way
    store().applyState(state(0, { serverTime: T0 + 500 }));

    assert.equal(store().applySnapshot({ roomId: ROOM, sessionId: null, playback: null, serverTime: T0 + 400 }), false);
    assert.equal(store().playback.version, 0);
  });

  test('counts every snapshot of the room, so the lesson itself is loaded again', () => {
    store().applyState(state(5));
    store().applySnapshot({ roomId: ROOM, playback: state(5) });
    store().applySnapshot({ roomId: ROOM, playback: null, serverTime: T0 });
    store().applySnapshot({ roomId: 'room-2', playback: null, serverTime: T0 });

    assert.equal(store().snapshotCount, 2);
  });

  test('ignores a snapshot of another room', () => {
    assert.equal(store().applySnapshot({ roomId: 'room-2', playback: state(1) }), false);
    assert.equal(store().playback, null);
  });

  test('a new lesson starts its versions afresh', () => {
    store().applyState(state(12));

    assert.equal(store().applyState(state(0, { sessionId: 'lesson-2', serverTime: T0 + 100 })), true);
    assert.equal(store().playback.sessionId, 'lesson-2');
  });

  test('a late state of an earlier lesson does not replace the new one', () => {
    store().applyState(state(0, { sessionId: 'lesson-2', serverTime: T0 + 100 }));

    assert.equal(store().applyState(state(12, { serverTime: T0 + 50 })), false);
    assert.equal(store().playback.sessionId, 'lesson-2');
  });

  test('an ended lesson clears the playback and its late states are dropped', () => {
    store().applyState(state(3));

    store().applyLessonEvent({ type: 'LESSON_ENDED', sessionId: 'lesson-1', reason: 'HOST_ENDED' });
    assert.equal(store().playback, null);
    assert.equal(store().applyState(state(4, { status: 'STOPPED' })), false);
    assert.equal(store().lessonEvent.type, 'LESSON_ENDED');
  });

  test('other lesson events keep the playback', () => {
    store().applyState(state(3));

    store().applyLessonEvent({ type: 'TAB_CHANGED', sessionId: 'lesson-1', tabId: 'tab-2' });
    assert.equal(store().playback.version, 3);
    assert.equal(store().lessonEvent.tabId, 'tab-2');
  });

  test('reset forgets everything of the previous room', () => {
    store().applyState(state(3));
    store().applyLessonEvent({ type: 'LESSON_ENDED', sessionId: 'lesson-1' });

    store().reset('room-2');
    assert.deepEqual(
      { roomId: store().roomId, playback: store().playback, lessonEvent: store().lessonEvent,
        endedSessions: store().endedSessions, snapshotCount: store().snapshotCount },
      { roomId: 'room-2', playback: null, lessonEvent: null, endedSessions: [], snapshotCount: 0 },
    );
  });
});

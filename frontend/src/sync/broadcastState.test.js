import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { expectedPosition, hostPlayback, isNewer, seekTarget, shouldPlay } from './broadcastState.js';

const active = { status: 'ACTIVE', trackId: 't1', position: 60, playing: false, anchorTime: 1_000_000, version: 3 };

describe('isNewer', () => {
  test('applies only versions after the last applied one', () => {
    assert.equal(isNewer(active, null), true);
    assert.equal(isNewer(active, 2), true);
    assert.equal(isNewer(active, 3), false);
    assert.equal(isNewer(active, 4), false);
  });

  test('always applies the end of a broadcast, also one the server did not know', () => {
    assert.equal(isNewer({ status: 'TERMINATED', version: 0 }, 9), true);
  });

  test('ignores nothing', () => {
    assert.equal(isNewer(null, 1), false);
  });
});

describe('expectedPosition', () => {
  test('stays at the anchored position while paused (the reported case)', () => {
    assert.equal(expectedPosition(active, 1_090_000), 60);
  });

  test('advances with the server clock while playing', () => {
    assert.equal(expectedPosition({ ...active, playing: true }, 1_002_500), 62.5);
  });

  test('never goes before the anchor when the clocks disagree', () => {
    assert.equal(expectedPosition({ ...active, playing: true }, 999_000), 60);
  });
});

describe('seekTarget', () => {
  test('leaves a player within the threshold alone', () => {
    assert.equal(seekTarget(60.4, 60, 180), null);
    assert.equal(seekTarget(59.2, 60, 180), null);
  });

  test('seeks a player that drifted further', () => {
    assert.equal(seekTarget(55, 60, 180), 60);
    assert.equal(seekTarget(0, 60, 180), 60);
  });

  test('does not run past the end of the track while the next one is on its way', () => {
    assert.equal(seekTarget(170, 200, 180), 179.75);
  });

  test('works before the duration is known', () => {
    assert.equal(seekTarget(0, 30, NaN), 30);
  });
});

describe('shouldPlay', () => {
  test('plays only an active broadcast with a playing track that the listener has not paused', () => {
    assert.equal(shouldPlay({ ...active, playing: true }, false), true);
    assert.equal(shouldPlay(active, false), false);
    assert.equal(shouldPlay({ ...active, playing: true }, true), false);
    assert.equal(shouldPlay({ ...active, playing: true, trackId: null }, false), false);
    assert.equal(shouldPlay({ ...active, playing: true, status: 'TERMINATED' }, false), false);
    assert.equal(shouldPlay(null, false), false);
  });
});

describe('hostPlayback', () => {
  test('reports the track, the position and whether it plays', () => {
    assert.deepEqual(hostPlayback('t1', { currentTime: 12.5, paused: false }),
      { trackId: 't1', position: 12.5, playing: true });
    assert.deepEqual(hostPlayback('t1', { currentTime: 3, paused: true }),
      { trackId: 't1', position: 3, playing: false });
  });

  test('reports a paused start without a track or a player', () => {
    assert.deepEqual(hostPlayback(null, null), { trackId: null, position: 0, playing: false });
    assert.deepEqual(hostPlayback(undefined, { currentTime: NaN, paused: false }),
      { trackId: null, position: 0, playing: false });
  });
});

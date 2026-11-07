import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import {
  isNewerState,
  measureTable,
  MAX_DRIFT_MS,
  needsResync,
  positionAt,
  quarterBeatsAt,
  toCanvasPosition,
} from './playbackMath.js';

const ANCHOR = 1_700_000_000_000;

/** A playback state as the server sends it: 4/4 at 120 BPM, so a quarter beat lasts 500 ms. */
function state(overrides = {}) {
  return {
    sessionId: 'lesson-1',
    measure: 1,
    beat: 0,
    tempoBpm: 120,
    beatsPerMeasure: 4,
    beatUnit: 4,
    status: 'PLAYING',
    anchorTime: ANCHOR,
    metronomeOn: false,
    version: 1,
    serverTime: ANCHOR,
    ...overrides,
  };
}

describe('positionAt', () => {
  test('moves on by tempo while playing', () => {
    assert.deepEqual(positionAt(state(), ANCHOR + 1_250), { measure: 1, beat: 2.5 });
  });

  test('wraps beats into measures', () => {
    // 10 quarter beats: two full 4/4 measures and two beats
    assert.deepEqual(positionAt(state({ measure: 3 }), ANCHOR + 5_000), { measure: 5, beat: 2 });
  });

  test('starts from the stored beat', () => {
    assert.deepEqual(positionAt(state({ beat: 3.5 }), ANCHOR + 250), { measure: 2, beat: 0 });
  });

  test('a measure of 6/8 holds 3 quarter beats', () => {
    assert.deepEqual(positionAt(state({ beatsPerMeasure: 6, beatUnit: 8 }), ANCHOR + 2_000),
      { measure: 2, beat: 1 });
  });

  test('stays at the stored position during the count-in, before the anchor', () => {
    assert.deepEqual(positionAt(state({ measure: 7, beat: 1 }), ANCHOR - 1_000), { measure: 7, beat: 1 });
  });

  test('paused and stopped playback does not move', () => {
    assert.deepEqual(positionAt(state({ status: 'PAUSED', beat: 2 }), ANCHOR + 60_000), { measure: 1, beat: 2 });
    assert.deepEqual(positionAt(state({ status: 'STOPPED' }), ANCHOR + 60_000), { measure: 1, beat: 0 });
  });

  test('lands on the next measure exactly at a boundary', () => {
    assert.deepEqual(positionAt(state({ tempoBpm: 90 }), ANCHOR + 8_000), { measure: 4, beat: 0 });
  });

  test('a beat never reaches the measure length, whatever the rounding', () => {
    for (let ms = 0; ms < 20_000; ms += 7) {
      const { beat } = positionAt(state({ tempoBpm: 97, beatsPerMeasure: 7, beatUnit: 8 }), ANCHOR + ms);
      assert.ok(beat >= 0 && beat < 3.5, `beat ${beat} at ${ms} ms`);
    }
  });

  test('two clients with the same clock see the same position', () => {
    const received = state({ measure: 12, beat: 1.5, tempoBpm: 133 });
    const sameState = JSON.parse(JSON.stringify(received));

    assert.deepEqual(positionAt(received, ANCHOR + 4_321), positionAt(sameState, ANCHOR + 4_321));
  });
});

describe('toCanvasPosition', () => {
  test('counts measures from 0', () => {
    assert.deepEqual(toCanvasPosition({ measure: 1, beat: 2.5 }, measureTable(state())), { measure: 0, beat: 2.5 });
  });

  test('counts beats in the beat unit of the time signature', () => {
    // 1.5 quarter beats into a 6/8 measure is the fourth eighth-note beat
    assert.deepEqual(toCanvasPosition({ measure: 3, beat: 1.5 }, measureTable(state({ beatsPerMeasure: 6, beatUnit: 8 }))),
      { measure: 2, beat: 3 });
  });
});

describe('isNewerState', () => {
  test('takes the first state', () => {
    assert.equal(isNewerState(null, state()), true);
  });

  test('takes a higher version of the same lesson', () => {
    assert.equal(isNewerState(state({ version: 4 }), state({ version: 5 })), true);
  });

  test('drops a version already applied, or an older one', () => {
    assert.equal(isNewerState(state({ version: 5 }), state({ version: 5 })), false);
    assert.equal(isNewerState(state({ version: 5 }), state({ version: 3 })), false);
  });

  test('takes a state of another lesson, whose versions count afresh', () => {
    assert.equal(isNewerState(state({ version: 9 }), state({ sessionId: 'lesson-2', version: 0, serverTime: ANCHOR + 1 })),
      true);
  });

  test('drops a late state of an older lesson, whatever its version', () => {
    const applied = state({ sessionId: 'lesson-2', version: 0, serverTime: ANCHOR + 5_000 });

    assert.equal(isNewerState(applied, state({ sessionId: 'lesson-1', version: 9, serverTime: ANCHOR + 4_000 })),
      false);
  });

  test('drops nothing at all', () => {
    assert.equal(isNewerState(state(), null), false);
  });
});

describe('needsResync', () => {
  const frame = (serverTime, localTime) => ({ serverTime, localTime });

  test('not on the first frame', () => {
    assert.equal(needsResync(null, frame(ANCHOR, 100)), false);
  });

  test('not while both clocks move on together', () => {
    assert.equal(needsResync(frame(ANCHOR, 100), frame(ANCHOR + 16, 116)), false);
  });

  test('not for a small clock correction', () => {
    assert.equal(needsResync(frame(ANCHOR, 100), frame(ANCHOR + 16 + MAX_DRIFT_MS, 116)), false);
  });

  test('when the server clock jumps away from the monotonic one by more than 150 ms', () => {
    assert.equal(needsResync(frame(ANCHOR, 100), frame(ANCHOR + 17 + MAX_DRIFT_MS, 116)), true);
    assert.equal(needsResync(frame(ANCHOR, 100), frame(ANCHOR - 200, 116)), true);
  });

  test('not for slow frames, while both clocks agree', () => {
    assert.equal(needsResync(frame(ANCHOR, 100), frame(ANCHOR + 400, 500)), false);
    assert.equal(needsResync(frame(ANCHOR, 100), frame(ANCHOR + 30_000, 30_100)), false);
  });

  test('when the monotonic clock stood still while the wall clock went on, as in a laptop sleep', () => {
    assert.equal(needsResync(frame(ANCHOR, 100), frame(ANCHOR + 60_000, 116)), true);
  });
});

describe('measures of different lengths (KAN-95)', () => {
  // 4/4, 4/4, 3/4, 6/8, then 4/4 again: 4 + 4 + 3 + 3 + 4 quarter beats; at 120 BPM a quarter beat lasts 500 ms
  const SIGNATURES = [
    { beatsPerMeasure: 4, beatUnit: 4 },
    { beatsPerMeasure: 4, beatUnit: 4 },
    { beatsPerMeasure: 3, beatUnit: 4 },
    { beatsPerMeasure: 6, beatUnit: 8 },
    { beatsPerMeasure: 4, beatUnit: 4 },
  ];
  const table = measureTable(state(), SIGNATURES);

  test('measures start where the earlier ones end', () => {
    assert.deepEqual([1, 2, 3, 4, 5, 6].map(table.startOf), [0, 4, 8, 11, 14, 18]);
  });

  test('playing over a change of time signature', () => {
    const playing = state();

    assert.deepEqual(positionAt(playing, ANCHOR + 4_000, table), { measure: 3, beat: 0 });
    assert.deepEqual(positionAt(playing, ANCHOR + 5_000, table), { measure: 3, beat: 2 });
    // The 3/4 measure ends after 3 beats, not 4
    assert.deepEqual(positionAt(playing, ANCHOR + 5_500, table), { measure: 4, beat: 0 });
    assert.deepEqual(positionAt(playing, ANCHOR + 7_250, table), { measure: 5, beat: 0.5 });
  });

  test('seeking into a measure after a change', () => {
    const seeked = state({ measure: 4 });

    assert.deepEqual(positionAt(seeked, ANCHOR + 1_000, table), { measure: 4, beat: 2 });
    assert.deepEqual(positionAt(seeked, ANCHOR + 1_500, table), { measure: 5, beat: 0 });
  });

  test('wraps a stored position the server did not wrap', () => {
    // Paused 10 quarter beats into measure 1: past measures 1 and 2, 2 beats into the 3/4 measure
    const paused = state({ status: 'PAUSED', beat: 10 });

    assert.deepEqual(positionAt(paused, ANCHOR + 60_000, table), { measure: 3, beat: 2 });
  });

  test('measures past the list keep its last time signature', () => {
    assert.deepEqual(positionAt(state({ measure: 7 }), ANCHOR + 2_500, table), { measure: 8, beat: 1 });
  });

  test('without a list every measure has the state\'s time signature', () => {
    const threeFour = state({ beatsPerMeasure: 3, beatUnit: 4 });

    assert.deepEqual(positionAt(threeFour, ANCHOR + 4_000), { measure: 3, beat: 2 });
    assert.deepEqual(positionAt(threeFour, ANCHOR + 4_000, measureTable(threeFour, [])), { measure: 3, beat: 2 });
  });

  test('a beat never reaches the length of its measure, whatever the rounding', () => {
    for (let ms = 0; ms < 30_000; ms += 7) {
      const { measure, beat } = positionAt(state({ tempoBpm: 97, beat: 0.1 }), ANCHOR + ms, table);
      const length = (table.signatureOf(measure).beatsPerMeasure * 4) / table.signatureOf(measure).beatUnit;
      assert.ok(beat >= 0 && beat < length, `beat ${beat} of measure ${measure} at ${ms} ms`);
    }
  });

  test('the canvas counts each measure in its own beat unit', () => {
    // 1.5 quarter beats into the 6/8 measure is its fourth eighth-note beat; in the 3/4 measure it is beat 1.5
    assert.deepEqual(toCanvasPosition({ measure: 4, beat: 1.5 }, table), { measure: 3, beat: 3 });
    assert.deepEqual(toCanvasPosition({ measure: 3, beat: 1.5 }, table), { measure: 2, beat: 1.5 });
  });

  test('quarter beats from the start of the tab', () => {
    assert.equal(quarterBeatsAt(state({ measure: 4, beat: 1 }), ANCHOR + 500, table), 13);
  });
});

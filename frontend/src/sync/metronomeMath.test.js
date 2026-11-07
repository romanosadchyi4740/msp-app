import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { audioTimeFor, clickIntervalMs, clicksBetween, SAME_CLICK_MS, withoutPlayed } from './metronomeMath.js';
import { positionAt } from './playbackMath.js';

const ANCHOR = 1_700_000_000_000;

/** 4/4 at 120 BPM: a click every 500 ms, a measure every 2 s. */
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
    metronomeOn: true,
    version: 1,
    serverTime: ANCHOR,
    ...overrides,
  };
}

const times = (clicks) => clicks.map((click) => click.time - ANCHOR);

describe('clickIntervalMs', () => {
  test('one beat of the time signature at the tempo in quarter notes', () => {
    assert.equal(clickIntervalMs(state()), 500);
    assert.equal(clickIntervalMs(state({ beatsPerMeasure: 6, beatUnit: 8 })), 250);
    assert.equal(clickIntervalMs(state({ tempoBpm: 90 })), 60000 / 90);
  });
});

describe('clicksBetween', () => {
  test('clicks every beat from the anchor, accenting the downbeat', () => {
    const clicks = clicksBetween(state(), ANCHOR - 1, ANCHOR + 2_000);

    assert.deepEqual(times(clicks), [0, 500, 1_000, 1_500, 2_000]);
    assert.deepEqual(clicks.map((click) => click.accent), [true, false, false, false, true]);
    assert.ok(clicks.every((click) => !click.countIn));
  });

  test('the window is open at its start and closed at its end, so consecutive windows never repeat a click', () => {
    const first = clicksBetween(state(), ANCHOR - 1, ANCHOR + 500);
    const second = clicksBetween(state(), ANCHOR + 500, ANCHOR + 1_000);

    assert.deepEqual(times(first), [0, 500]);
    assert.deepEqual(times(second), [1_000]);
  });

  test('a 100 ms lookahead window holds at most the one click due', () => {
    assert.deepEqual(times(clicksBetween(state(), ANCHOR + 1_450, ANCHOR + 1_550)), [1_500]);
    assert.deepEqual(times(clicksBetween(state(), ANCHOR + 1_510, ANCHOR + 1_610)), []);
  });

  test('counts in one measure before the anchor, then plays on', () => {
    const clicks = clicksBetween(state(), ANCHOR - 2_500, ANCHOR + 600);

    assert.deepEqual(times(clicks), [-2_000, -1_500, -1_000, -500, 0, 500]);
    assert.deepEqual(clicks.map((click) => click.countIn), [true, true, true, true, false, false]);
    assert.deepEqual(clicks.map((click) => click.accent), [true, false, false, false, true, false]);
  });

  test('counts in even with the metronome off, and is silent after the anchor', () => {
    const clicks = clicksBetween(state({ metronomeOn: false }), ANCHOR - 2_500, ANCHOR + 2_000);

    assert.deepEqual(times(clicks), [-2_000, -1_500, -1_000, -500]);
  });

  test('resumed mid-beat, counts in on the beat grid continued backwards, so the pulse runs on', () => {
    // Paused half a beat into the measure: the count-in keeps the beats of the measure, not of the anchor
    const clicks = clicksBetween(state({ beat: 0.5 }), ANCHOR - 2_500, ANCHOR + 800);

    assert.deepEqual(times(clicks), [-1_750, -1_250, -750, -250, 250, 750]);
    assert.deepEqual(clicks.map((click) => click.countIn), [true, true, true, true, false, false]);
    assert.equal(clicks[0].accent, true);
    // Within the measure counted in, which starts at the end of the lead
    assert.ok(clicks[0].time >= ANCHOR - 2_000);
  });

  test('a 6/8 measure clicks six eighth notes, accenting the first', () => {
    const clicks = clicksBetween(state({ beatsPerMeasure: 6, beatUnit: 8 }), ANCHOR - 1, ANCHOR + 1_499);

    assert.deepEqual(times(clicks), [0, 250, 500, 750, 1_000, 1_250]);
    assert.deepEqual(clicks.map((click) => click.accent), [true, false, false, false, false, false]);
  });

  test('resumed mid-beat, clicks on the next beat of the grid', () => {
    // Paused at 1.5 quarter beats into measure 3; the next beats are 2 and 3, then the downbeat of measure 4
    const clicks = clicksBetween(state({ measure: 3, beat: 1.5 }), ANCHOR - 1, ANCHOR + 1_300);

    assert.deepEqual(times(clicks), [250, 750, 1_250]);
    assert.deepEqual(clicks.map((click) => click.accent), [false, false, true]);
  });

  test('a playback that started long ago clicks on its grid', () => {
    const clicks = clicksBetween(state(), ANCHOR + 3_600_000 + 1_960, ANCHOR + 3_600_000 + 2_060);

    assert.deepEqual(times(clicks), [3_600_000 + 2_000]);
    assert.equal(clicks[0].accent, true);
  });

  test('paused and stopped playback does not click', () => {
    assert.deepEqual(clicksBetween(state({ status: 'PAUSED' }), ANCHOR - 3_000, ANCHOR + 3_000), []);
    assert.deepEqual(clicksBetween(state({ status: 'STOPPED' }), ANCHOR - 3_000, ANCHOR + 3_000), []);
  });

  test('no clicks without a state or in an empty window', () => {
    assert.deepEqual(clicksBetween(null, ANCHOR, ANCHOR + 1_000), []);
    assert.deepEqual(clicksBetween(state(), ANCHOR + 1_000, ANCHOR + 1_000), []);
  });

  test('a tempo change carries on from the beat reached, without a jump', () => {
    // At 120 BPM until 1 250 ms after the anchor (2.5 quarter beats into measure 1), then 90 BPM, re-anchored there
    // as the server does it
    const before = state();
    const changeAt = ANCHOR + 1_250;
    const reached = positionAt(before, changeAt);
    const after = state({ ...reached, tempoBpm: 90, anchorTime: changeAt });

    const beforeClicks = times(clicksBetween(before, ANCHOR - 1, changeAt));
    const afterClicks = times(clicksBetween(after, changeAt, changeAt + 2_000));

    assert.deepEqual(beforeClicks, [0, 500, 1_000]);
    // 2.5 quarter beats in: half a beat at 90 BPM (333 ms) to the fourth beat, then a beat every 667 ms, the next
    // being the downbeat of measure 2
    const ms = 60000 / 90;
    assert.deepEqual(afterClicks.map(Math.round), [Math.round(1_250 + ms / 2), Math.round(1_250 + 1.5 * ms),
      Math.round(1_250 + 2.5 * ms)]);
    assert.deepEqual(clicksBetween(after, changeAt, changeAt + 2_000).map((click) => click.accent),
      [false, true, false]);
  });

  test('two participants with the same state and clock click at the same times', () => {
    const received = state({ measure: 7, beat: 0.75, tempoBpm: 133, anchorTime: ANCHOR + 123 });
    const sameState = JSON.parse(JSON.stringify(received));

    assert.deepEqual(clicksBetween(received, ANCHOR, ANCHOR + 10_000), clicksBetween(sameState, ANCHOR, ANCHOR + 10_000));
  });
});

describe('withoutPlayed', () => {
  test('drops clicks already played, also when planned again a little differently', () => {
    const clicks = [{ time: ANCHOR }, { time: ANCHOR + 500 }];

    assert.deepEqual(withoutPlayed(clicks, [ANCHOR + SAME_CLICK_MS - 1]), [{ time: ANCHOR + 500 }]);
  });

  test('drops a late click close to one heard, as after a tempo change, but keeps it when it is on time', () => {
    // The old state clicked at ANCHOR + 5; the new grid has a click at ANCHOR + 30, planned at ANCHOR + 40
    const clicks = [{ time: ANCHOR + 30 }];

    assert.deepEqual(withoutPlayed(clicks, [ANCHOR + 5], ANCHOR + 40), []);
    assert.deepEqual(withoutPlayed(clicks, [ANCHOR + 5], ANCHOR + 20), clicks);
    assert.deepEqual(withoutPlayed(clicks, [ANCHOR + 5]), clicks);
  });

  test('keeps clicks further away than a click from any played', () => {
    const clicks = [{ time: ANCHOR }, { time: ANCHOR + 500 }];

    assert.deepEqual(withoutPlayed(clicks, [ANCHOR - SAME_CLICK_MS]), clicks);
    assert.deepEqual(withoutPlayed(clicks, []), clicks);
  });
});

describe('audioTimeFor', () => {
  test('maps a server time to the context time heard then', () => {
    // Context time 10 s is heard at performance time 5 000 ms, which is server time ANCHOR
    const timestamp = { contextTime: 10, performanceTime: 5_000 };
    const serverMinusPerformance = ANCHOR - 5_000;

    assert.equal(audioTimeFor(ANCHOR, timestamp, serverMinusPerformance), 10);
    assert.equal(audioTimeFor(ANCHOR + 250, timestamp, serverMinusPerformance), 10.25);
    assert.equal(audioTimeFor(ANCHOR - 1_000, timestamp, serverMinusPerformance), 9);
  });
});

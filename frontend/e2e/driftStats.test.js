import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { driftReport, percentile, positionAtWallTime } from './driftStats.js';

const T0 = 1_000_000;

/** Samples of one client playing at 120 BPM (2 quarter beats per second) from the anchor, every 20 ms. */
function playing({ offsetBeats = 0, version = 2, anchorTime = T0, from = T0, to = T0 + 2_000 } = {}) {
  const samples = [];
  for (let wallTime = from; wallTime <= to; wallTime += 20) {
    const elapsed = Math.max(0, wallTime - anchorTime);
    samples.push({
      wallTime, serverTime: wallTime, anchorTime, version, status: 'PLAYING', tempoBpm: 120,
      quarterBeats: offsetBeats + (elapsed * 120) / 60000,
    });
  }
  return samples;
}

describe('percentile', () => {
  test('nearest rank', () => {
    const values = [5, 1, 4, 2, 3, 10, 9, 8, 7, 6];

    assert.equal(percentile(values, 50), 5);
    assert.equal(percentile(values, 95), 10);
    assert.equal(percentile(values, 0), 1);
    assert.equal(percentile([], 50), null);
  });
});

describe('positionAtWallTime', () => {
  test('interpolates within one state', () => {
    assert.equal(positionAtWallTime(playing(), T0 + 510), 1.02);
  });

  test('takes the nearer sample across a change of state', () => {
    const samples = [
      { wallTime: T0, quarterBeats: 1, version: 1, status: 'PLAYING' },
      { wallTime: T0 + 20, quarterBeats: 40, version: 2, status: 'PLAYING' },
    ];

    assert.equal(positionAtWallTime(samples, T0 + 5), 1);
    assert.equal(positionAtWallTime(samples, T0 + 15), 40);
  });

  test('is null outside the samples', () => {
    assert.equal(positionAtWallTime(playing(), T0 - 1), null);
  });
});

describe('driftReport', () => {
  test('clients a quarter of a beat apart drift 125 ms at 120 BPM', () => {
    const report = driftReport({ host: playing(), other: playing({ offsetBeats: 0.25 }) });

    assert.equal(report.all.p50, 125);
    assert.equal(report.all.max, 125);
    assert.ok(report.all.points > 150);
  });

  test('clients in step do not drift', () => {
    assert.equal(driftReport({ host: playing(), other: playing() }).all.max, 0);
  });

  test('leaves out the count-in, where every client is still at the stored position', () => {
    // The anchor is 1 s into the 2 s of samples: the first second is a count-in, the second is counted (every 10 ms)
    const withCountIn = driftReport({
      host: playing({ anchorTime: T0 + 1_000 }),
      other: playing({ anchorTime: T0 + 1_000 }),
    });
    const withoutCountIn = driftReport({ host: playing(), other: playing() });

    assert.equal(withoutCountIn.all.points, 201);
    assert.equal(withCountIn.all.points, 101);
  });

  test('reports the moments right after a command separately', () => {
    const report = driftReport({ host: playing(), other: playing({ offsetBeats: 0.25 }) },
      { commandTimes: [T0 + 1_000], settleMs: 300 });

    assert.equal(report.all.points - report.steady.points, 30);
  });
});

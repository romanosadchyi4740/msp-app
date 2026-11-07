import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { addEstimate, averageOffset, bestSample, HISTORY_SIZE, sampleOf } from './clockMath.js';

/**
 * A request sent at local time t0 to a server whose clock is `offset` ahead, taking `up` ms to arrive and `down` ms
 * to come back; returns the sample the client computes.
 */
function exchange(t0, offset, up, down) {
  const serverTime = t0 + up + offset;
  const t1 = t0 + up + down;
  return sampleOf(t0, serverTime, t1);
}

describe('sampleOf', () => {
  test('symmetric delays give the exact offset', () => {
    const sample = exchange(1_000_000, 250, 20, 20);

    assert.equal(sample.offset, 250);
    assert.equal(sample.rtt, 40);
  });

  test('a server clock behind the local one gives a negative offset', () => {
    assert.equal(exchange(1_000_000, -3_600_000, 5, 5).offset, -3_600_000);
  });

  test('asymmetric delays err by half their difference, within rtt / 2', () => {
    const sample = exchange(1_000_000, 250, 30, 10);

    assert.equal(sample.offset, 260);
    assert.ok(Math.abs(sample.offset - 250) <= sample.rtt / 2);
  });

  test('the worst split still stays within rtt / 2', () => {
    const sample = exchange(1_000_000, 250, 0, 80);

    assert.equal(Math.abs(sample.offset - 250), sample.rtt / 2);
  });
});

describe('bestSample', () => {
  test('keeps the sample with the smallest round trip', () => {
    const samples = [exchange(0, 100, 40, 10), exchange(100, 100, 3, 4), exchange(200, 100, 15, 60)];

    assert.equal(bestSample(samples).rtt, 7);
  });

  test('is null for a round without answers', () => {
    assert.equal(bestSample([]), null);
  });

  test('8 jittered samples on a local network come within 10 ms of the true offset', () => {
    // Delays of a busy LAN: mostly 1-5 ms each way, with spikes and lopsided queueing
    const delays = [[12, 3], [2, 9], [40, 2], [3, 2], [1, 25], [6, 30], [2, 3], [18, 18]];
    const samples = delays.map(([up, down], i) => exchange(1_000_000 + i * 100, 742, up, down));

    const best = bestSample(samples);

    assert.ok(Math.abs(best.offset - 742) < 10, `offset ${best.offset}`);
    assert.ok(best.rtt <= 5);
  });
});

describe('moving average', () => {
  test('an empty history has no offset', () => {
    assert.equal(averageOffset([]), 0);
  });

  test('averages the estimates that agree', () => {
    let history = [];
    for (const offset of [100, 104, 98, 102]) {
      history = addEstimate(history, { offset, rtt: 10 });
    }

    assert.equal(history.length, 4);
    assert.equal(averageOffset(history), 101);
  });

  test(`keeps the last ${HISTORY_SIZE} estimates`, () => {
    let history = [];
    for (const offset of [100, 101, 102, 103, 104, 105]) {
      history = addEstimate(history, { offset, rtt: 10 });
    }

    assert.deepEqual(history.map((estimate) => estimate.offset), [102, 103, 104, 105]);
  });

  test('restarts from an estimate that cannot agree, as after a jump of the local clock', () => {
    let history = [{ offset: 100, rtt: 10 }, { offset: 102, rtt: 8 }];

    history = addEstimate(history, { offset: 2_100, rtt: 10 });

    assert.deepEqual(history, [{ offset: 2_100, rtt: 10 }]);
  });

  test('keeps a noisy estimate whose error bound still overlaps the average', () => {
    // 60 ms away from the average, but a 120 ms round trip allows that much error
    const history = addEstimate([{ offset: 100, rtt: 10 }], { offset: 160, rtt: 120 });

    assert.equal(history.length, 2);
    assert.equal(averageOffset(history), 130);
  });
});

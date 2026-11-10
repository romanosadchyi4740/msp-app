import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { averageRtt, GOOD, lossPercentBetween, POOR, qualityOf } from './quality.js';

const connected = (figures) => ({ state: 'connected', rttMs: 20, jitterMs: 5, lossPercent: 0, ...figures });

describe('qualityOf', () => {
  test('a quick, steady, lossless connection is good', () => {
    assert.equal(qualityOf(connected()), 'good');
  });

  test('the limits themselves still count as the better grade', () => {
    assert.equal(qualityOf(connected(GOOD)), 'good');
    assert.equal(qualityOf(connected(POOR)), 'fair');
  });

  test('one figure above the good limit makes it fair', () => {
    assert.equal(qualityOf(connected({ rttMs: GOOD.rttMs + 1 })), 'fair');
    assert.equal(qualityOf(connected({ jitterMs: GOOD.jitterMs + 1 })), 'fair');
    assert.equal(qualityOf(connected({ lossPercent: GOOD.lossPercent + 0.1 })), 'fair');
  });

  test('one figure above the poor limit makes it poor, whatever the others are', () => {
    assert.equal(qualityOf(connected({ rttMs: POOR.rttMs + 1 })), 'poor');
    assert.equal(qualityOf(connected({ jitterMs: POOR.jitterMs + 1 })), 'poor');
    assert.equal(qualityOf(connected({ lossPercent: POOR.lossPercent + 0.1 })), 'poor');
  });

  test('figures not measured yet do not count', () => {
    assert.equal(qualityOf({ state: 'connected', rttMs: null, jitterMs: null, lossPercent: null }), 'good');
    assert.equal(qualityOf({ state: 'connected' }), 'good');
    assert.equal(qualityOf({ state: 'connected', rttMs: null, lossPercent: 20 }), 'poor');
  });

  test('a connection that is not up has no grade', () => {
    for (const state of ['new', 'connecting', 'disconnected', 'failed', 'closed', undefined]) {
      assert.equal(qualityOf({ state, rttMs: 20, jitterMs: 5, lossPercent: 0 }), null);
    }
  });
});

describe('lossPercentBetween', () => {
  const reading = (packetsReceived, packetsLost) => ({ packetsReceived, packetsLost });

  test('is the lost share of the packets expected since the earlier reading', () => {
    // 100 more expected, 10 of them lost
    assert.equal(lossPercentBetween(reading(1000, 5), reading(1090, 15)), 10);
  });

  test('ignores the losses before the earlier reading', () => {
    assert.equal(lossPercentBetween(reading(1000, 400), reading(1100, 400)), 0);
  });

  test('keeps one decimal', () => {
    assert.equal(lossPercentBetween(reading(0, 0), reading(299, 1)), 0.3);
  });

  test('is 100 when nothing arrived', () => {
    assert.equal(lossPercentBetween(reading(1000, 0), reading(1000, 50)), 100);
  });

  test('is null without an earlier reading or without packets in between', () => {
    assert.equal(lossPercentBetween(null, reading(100, 0)), null);
    assert.equal(lossPercentBetween(undefined, reading(100, 0)), null);
    assert.equal(lossPercentBetween(reading(100, 0), reading(100, 0)), null);
  });

  test('counts a loss counter that went down, as late packets arrived, as no loss', () => {
    assert.equal(lossPercentBetween(reading(1000, 20), reading(1100, 15)), 0);
  });

  test('is null when the counters started over with a new connection', () => {
    assert.equal(lossPercentBetween(reading(5000, 20), reading(40, 0)), null);
  });
});

describe('averageRtt', () => {
  test('averages the measured times and rounds', () => {
    assert.equal(averageRtt([40, 61]), 51);
    assert.equal(averageRtt([0]), 0);
  });

  test('leaves out what was not measured', () => {
    assert.equal(averageRtt([40, null, undefined, NaN, 60]), 50);
  });

  test('is null when nothing was measured', () => {
    assert.equal(averageRtt([]), null);
    assert.equal(averageRtt([null, undefined]), null);
  });
});

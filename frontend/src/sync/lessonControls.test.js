import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import {
  MAX_TEMPO_BPM, MIN_TEMPO_BPM, parseMeasure, shortcutCommand, stepMeasure, stepTempo, timeLeft,
} from './lessonControls.js';

const lesson = { status: 'PAUSED', measure: 4, tempoBpm: 100, measureCount: 10 };

function key(name, extra = {}) {
  return { key: name, target: { tagName: 'BODY' }, ...extra };
}

describe('stepTempo', () => {
  test('steps the tempo', () => {
    assert.equal(stepTempo(100, 5), 105);
    assert.equal(stepTempo(100, -5), 95);
  });

  test('stays within the range the server accepts', () => {
    assert.equal(stepTempo(MAX_TEMPO_BPM - 2, 5), MAX_TEMPO_BPM);
    assert.equal(stepTempo(MIN_TEMPO_BPM + 2, -5), MIN_TEMPO_BPM);
  });
});

describe('parseMeasure', () => {
  test('reads a whole measure number', () => {
    assert.equal(parseMeasure(' 7 '), 7);
    assert.equal(parseMeasure('10', 10), 10);
  });

  test('refuses what is not a measure of the tab', () => {
    for (const text of ['', '0', '-1', '2.5', 'abc', '1e3', null]) {
      assert.equal(parseMeasure(text), null, String(text));
    }
    assert.equal(parseMeasure('11', 10), null);
  });

  test('takes any measure from 1 while the measure count is unknown', () => {
    assert.equal(parseMeasure('500', null), 500);
  });
});

describe('stepMeasure', () => {
  test('stays between the first and the last measure', () => {
    assert.equal(stepMeasure(1, -1, 10), 1);
    assert.equal(stepMeasure(10, 1, 10), 10);
    assert.equal(stepMeasure(10, 1, null), 11);
  });
});

describe('shortcutCommand', () => {
  test('space plays a paused or stopped lesson and pauses a playing one', () => {
    assert.deepEqual(shortcutCommand(key(' '), lesson), { type: 'PLAY', argument: null });
    assert.deepEqual(shortcutCommand(key(' '), { ...lesson, status: 'STOPPED' }), { type: 'PLAY', argument: null });
    assert.deepEqual(shortcutCommand(key(' '), { ...lesson, status: 'PLAYING' }), { type: 'PAUSE', argument: null });
  });

  test('left and right go one measure back and forward', () => {
    assert.deepEqual(shortcutCommand(key('ArrowLeft'), lesson), { type: 'SEEK', argument: '3' });
    assert.deepEqual(shortcutCommand(key('ArrowRight'), lesson), { type: 'SEEK', argument: '5' });
  });

  test('up and down change the tempo by a step', () => {
    assert.deepEqual(shortcutCommand(key('ArrowUp'), lesson), { type: 'SET_TEMPO', argument: '105' });
    assert.deepEqual(shortcutCommand(key('ArrowDown'), lesson), { type: 'SET_TEMPO', argument: '95' });
  });

  test('sends nothing at the ends of the tab and of the tempo range', () => {
    assert.equal(shortcutCommand(key('ArrowLeft'), { ...lesson, measure: 1 }), null);
    assert.equal(shortcutCommand(key('ArrowRight'), { ...lesson, measure: 10 }), null);
    assert.equal(shortcutCommand(key('ArrowUp'), { ...lesson, tempoBpm: MAX_TEMPO_BPM }), null);
    assert.equal(shortcutCommand(key('ArrowDown'), { ...lesson, tempoBpm: MIN_TEMPO_BPM }), null);
  });

  test('leaves keys meant for something else alone', () => {
    for (const tagName of ['INPUT', 'TEXTAREA', 'SELECT']) {
      assert.equal(shortcutCommand(key('ArrowUp', { target: { tagName } }), lesson), null, tagName);
    }
    assert.equal(shortcutCommand(key(' ', { target: { tagName: 'DIV', isContentEditable: true } }), lesson), null);
    assert.equal(shortcutCommand(key(' ', { target: { tagName: 'BUTTON' } }), lesson), null);
    for (const modifier of ['altKey', 'ctrlKey', 'metaKey', 'shiftKey', 'repeat']) {
      assert.equal(shortcutCommand(key('ArrowRight', { [modifier]: true }), lesson), null, modifier);
    }
    assert.equal(shortcutCommand(key('a'), lesson), null);
    assert.equal(shortcutCommand(key(' '), null), null);
  });

  test('arrows work on a focused button', () => {
    assert.deepEqual(shortcutCommand(key('ArrowRight', { target: { tagName: 'BUTTON' } }), lesson),
      { type: 'SEEK', argument: '5' });
  });
});

describe('timeLeft', () => {
  const endsAt = '2026-11-15T12:40:00Z';
  const at = (iso) => Date.parse(iso);

  test('counts down in minutes and seconds', () => {
    assert.equal(timeLeft(endsAt, at('2026-11-15T12:00:00Z')), '40:00');
    assert.equal(timeLeft(endsAt, at('2026-11-15T12:35:00.200Z')), '5:00', 'a started second counts');
    assert.equal(timeLeft(endsAt, at('2026-11-15T12:39:59Z')), '0:01');
  });

  test('shows hours for long lessons', () => {
    assert.equal(timeLeft(endsAt, at('2026-11-15T11:30:00Z')), '1:10:00');
  });

  test('never goes below zero', () => {
    assert.equal(timeLeft(endsAt, at('2026-11-15T13:00:00Z')), '0:00');
  });

  test('is null without a limit', () => {
    assert.equal(timeLeft(null, 0), null);
    assert.equal(timeLeft(undefined, 0), null);
  });
});

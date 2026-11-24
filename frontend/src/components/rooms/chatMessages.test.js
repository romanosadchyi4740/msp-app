import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { continuesPrevious, MAX_MESSAGE_LENGTH, mergeMessages, sendableText } from './chatMessages.js';

const message = (id, sentAt, authorId = 'alice', content = id) => ({ id, sentAt, authorId, content });

describe('mergeMessages', () => {
  test('puts a history page (newest first) oldest first', () => {
    const page = [message('c', '2026-11-15T10:03:00Z'), message('b', '2026-11-15T10:02:00Z'),
      message('a', '2026-11-15T10:01:00Z')];
    assert.deepEqual(mergeMessages([], page).map((m) => m.id), ['a', 'b', 'c']);
  });

  test('keeps every message once when pages and live messages overlap', () => {
    const shown = [message('a', '2026-11-15T10:01:00Z'), message('b', '2026-11-15T10:02:00Z')];
    const merged = mergeMessages(shown, [message('b', '2026-11-15T10:02:00Z'), message('c', '2026-11-15T10:03:00Z')]);
    assert.deepEqual(merged.map((m) => m.id), ['a', 'b', 'c']);
  });

  test('adds an older page in front', () => {
    const shown = [message('c', '2026-11-15T10:03:00Z')];
    const merged = mergeMessages(shown, [message('a', '2026-11-15T10:01:00Z')]);
    assert.deepEqual(merged.map((m) => m.id), ['a', 'c']);
  });

  test('orders messages of the same millisecond by their microseconds', () => {
    const merged = mergeMessages([], [message('a', '2026-11-15T10:00:00.123900Z'), message('b', '2026-11-15T10:00:00.123100Z')]);
    assert.deepEqual(merged.map((m) => m.id), ['b', 'a']);
  });

  test('orders messages of the same instant by id', () => {
    const at = '2026-11-15T10:00:00Z';
    assert.deepEqual(mergeMessages([], [message('y', at), message('x', at)]).map((m) => m.id), ['x', 'y']);
  });

  test('does not change the list it was given', () => {
    const shown = [message('a', '2026-11-15T10:01:00Z')];
    mergeMessages(shown, [message('b', '2026-11-15T10:02:00Z')]);
    assert.equal(shown.length, 1);
  });
});

describe('sendableText', () => {
  test('trims the text', () => {
    assert.equal(sendableText('  hello \n'), 'hello');
  });

  test('refuses empty and too long text', () => {
    assert.equal(sendableText(''), null);
    assert.equal(sendableText(' \n\t '), null);
    assert.equal(sendableText(undefined), null);
    assert.equal(sendableText('a'.repeat(MAX_MESSAGE_LENGTH + 1)), null);
    assert.equal(sendableText(`  ${'a'.repeat(MAX_MESSAGE_LENGTH)}  `), 'a'.repeat(MAX_MESSAGE_LENGTH));
  });
});

describe('continuesPrevious', () => {
  test('the same author within 5 minutes continues', () => {
    assert.equal(continuesPrevious(message('a', '2026-11-15T10:00:00Z'), message('b', '2026-11-15T10:04:59Z')), true);
  });

  test('another author, a longer pause or no previous message starts anew', () => {
    assert.equal(continuesPrevious(message('a', '2026-11-15T10:00:00Z'),
      message('b', '2026-11-15T10:01:00Z', 'bob')), false);
    assert.equal(continuesPrevious(message('a', '2026-11-15T10:00:00Z'), message('b', '2026-11-15T10:05:00Z')), false);
    assert.equal(continuesPrevious(null, message('b', '2026-11-15T10:05:00Z')), false);
  });
});

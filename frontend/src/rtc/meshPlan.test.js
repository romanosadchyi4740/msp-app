import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { isInitiator, isPolite, MAX_PEERS, planConnections } from './meshPlan.js';

const A = '0a000000-0000-0000-0000-000000000000';
const B = '0b000000-0000-0000-0000-000000000000';
const C = '0c000000-0000-0000-0000-000000000000';

const online = (userId) => ({ userId, online: true });
const offline = (userId) => ({ userId, online: false });

describe('roles', () => {
  test('exactly one of two participants is polite, and the other one starts the connection', () => {
    assert.equal(isPolite(A, B), false);
    assert.equal(isPolite(B, A), true);
    assert.equal(isInitiator(A, B), true);
    assert.equal(isInitiator(B, A), false);
  });
});

describe('planConnections', () => {
  test('calls the online participants this user is the initiator for', () => {
    const plan = planConnections(B, [online(A), online(B), online(C)], []);

    // B waits for A's offer and calls C
    assert.deepEqual(plan, { open: [C], close: [] });
  });

  test('does not call someone who is offline or already connected', () => {
    const plan = planConnections(A, [online(A), offline(B), online(C)], [{ userId: C, alive: true }]);

    assert.deepEqual(plan, { open: [], close: [] });
  });

  test('closes the connection to someone who left the room', () => {
    const plan = planConnections(A, [online(A), online(C)], [{ userId: B, alive: true }, { userId: C, alive: true }]);

    assert.deepEqual(plan, { open: [], close: [B] });
  });

  test('keeps a live connection to someone offline, and closes a dead one', () => {
    const participants = [online(A), offline(B), offline(C)];

    const plan = planConnections(A, participants, [{ userId: B, alive: true }, { userId: C, alive: false }]);

    assert.deepEqual(plan, { open: [], close: [C] });
  });

  test('calls again once a dead connection is closed and the peer is back online', () => {
    const plan = planConnections(A, [online(A), online(B)], [{ userId: B, alive: false }]);

    // Dead but online: the connection is left to its own recovery, not doubled
    assert.deepEqual(plan, { open: [], close: [] });
    assert.deepEqual(planConnections(A, [online(A), online(B)], []), { open: [B], close: [] });
  });

  test('never plans more than the room can hold', () => {
    const many = Array.from({ length: 7 }, (_, index) => online(`1${index}000000-0000-0000-0000-000000000000`));

    const plan = planConnections(A, [online(A), ...many], [{ userId: many[0].userId, alive: true }]);

    assert.equal(plan.open.length, MAX_PEERS - 1);
  });

  test('ignores this user among the participants', () => {
    assert.deepEqual(planConnections(A, [online(A)], []), { open: [], close: [] });
  });
});

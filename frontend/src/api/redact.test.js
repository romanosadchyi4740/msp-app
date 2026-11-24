import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { AxiosHeaders } from 'axios';
import { REDACTED, redactAxiosError, stompDebugLine } from './redact.js';

const TOKEN = 'eyJhbGciOiJIUzI1NiJ9.payload.signature';

function failedRequest() {
  const config = {
    url: '/api/rooms/1/messages',
    method: 'post',
    headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
    data: JSON.stringify({ content: 'private text', password: 'secret' }),
    suppressErrorToast: true,
  };
  return { name: 'AxiosError', message: 'Request failed with status code 409', config,
    response: { status: 409, data: { message: 'Room is full' }, config } };
}

describe('redactAxiosError', () => {
  test('removes the token and the request body', () => {
    const error = redactAxiosError(failedRequest());

    assert.equal(error.config.headers.Authorization, REDACTED);
    assert.equal(error.config.data, REDACTED);
    assert.doesNotMatch(JSON.stringify(error), /eyJ|Bearer|private text|secret/);
  });

  test('keeps what callers act on', () => {
    const error = redactAxiosError(failedRequest());

    assert.equal(error.response.status, 409);
    assert.equal(error.response.data.message, 'Room is full');
    assert.equal(error.config.url, '/api/rooms/1/messages');
    assert.equal(error.config.method, 'post');
    assert.equal(error.config.headers['Content-Type'], 'application/json');
    assert.equal(error.config.suppressErrorToast, true);
  });

  test('also cleans a separate response config', () => {
    const error = failedRequest();
    error.response.config = { ...error.config, headers: { ...error.config.headers } };

    redactAxiosError(error);

    assert.equal(error.response.config.headers.Authorization, REDACTED);
    assert.equal(error.response.config.data, REDACTED);
  });

  test('finds the header in any spelling, also in axios own header object', () => {
    const headers = new AxiosHeaders();
    headers.set('authorization', `Bearer ${TOKEN}`);
    const lowerCase = { config: { headers } };
    const plain = { config: { headers: { AUTHORIZATION: `Bearer ${TOKEN}` } } };

    redactAxiosError(lowerCase);
    redactAxiosError(plain);

    assert.equal(headers.get('Authorization'), REDACTED);
    assert.equal(plain.config.headers.AUTHORIZATION, REDACTED);
    assert.doesNotMatch(JSON.stringify(headers.toJSON()), /eyJ|Bearer/);
  });

  test('cleans a real axios header object set the way axiosClient sets it', () => {
    const headers = new AxiosHeaders({ 'Content-Type': 'application/json' });
    headers.Authorization = `Bearer ${TOKEN}`;

    redactAxiosError({ config: { headers } });

    assert.equal(headers.get('authorization'), REDACTED);
    assert.equal(headers.get('Content-Type'), 'application/json');
  });

  test('leaves a network error without a body or a token alone', () => {
    const error = { name: 'AxiosError', code: 'ERR_NETWORK', config: { url: '/api/x', headers: {} } };

    redactAxiosError(error);

    assert.equal(error.config.data, undefined);
    assert.equal('Authorization' in error.config.headers, false);
  });

  test('passes anything that is not an object through', () => {
    assert.equal(redactAxiosError(undefined), undefined);
    assert.equal(redactAxiosError('boom'), 'boom');
  });
});

describe('stompDebugLine', () => {
  test('keeps only the command line of a frame', () => {
    const connect = `>>> CONNECT\nAuthorization:Bearer ${TOKEN}\naccept-version:1.2\n\n`;
    const message = '<<< MESSAGE\ndestination:/topic/rooms/1/chat\ncontent-length:30\n\n{"content":"private text"}';

    assert.equal(stompDebugLine(connect), '>>> CONNECT');
    assert.equal(stompDebugLine(message), '<<< MESSAGE');
  });

  test('passes single-line status text through', () => {
    assert.equal(stompDebugLine('Web Socket Opened...'), 'Web Socket Opened...');
  });
});

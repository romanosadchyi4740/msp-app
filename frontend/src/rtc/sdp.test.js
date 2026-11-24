import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { OPUS_MUSIC_PARAMS, PTIME_MS, sessionIdOf, tuneOpusForMusic } from './sdp.js';

// What Chrome offers for one audio transceiver, shortened
const OFFER = [
  'v=0',
  'o=- 4611731400430051336 2 IN IP4 127.0.0.1',
  's=-',
  't=0 0',
  'a=group:BUNDLE 0',
  'm=audio 9 UDP/TLS/RTP/SAVPF 111 63 9',
  'c=IN IP4 0.0.0.0',
  'a=mid:0',
  'a=sendrecv',
  'a=rtpmap:111 opus/48000/2',
  'a=rtcp-fb:111 transport-cc',
  'a=fmtp:111 minptime=10;useinbandfec=1',
  'a=rtpmap:63 red/48000/2',
  'a=fmtp:63 111/111',
  'a=rtpmap:9 G722/8000',
  '',
].join('\r\n');

const fmtpOf = (sdp, type) => sdp.split(/\r?\n/).find((line) => line.startsWith(`a=fmtp:${type} `));

describe('tuneOpusForMusic', () => {
  test('adds the music parameters to the Opus payload type and keeps the ones it had', () => {
    const fmtp = fmtpOf(tuneOpusForMusic(OFFER), 111);

    assert.equal(fmtp,
      'a=fmtp:111 minptime=10;useinbandfec=1;stereo=1;sprop-stereo=1;maxaveragebitrate=128000;usedtx=0');
  });

  test('overrides a parameter the description set otherwise', () => {
    const speech = OFFER.replace('minptime=10;useinbandfec=1', 'usedtx=1;maxaveragebitrate=32000;stereo=0');

    const fmtp = fmtpOf(tuneOpusForMusic(speech), 111);

    assert.equal(fmtp, 'a=fmtp:111 usedtx=0;maxaveragebitrate=128000;stereo=1;sprop-stereo=1;useinbandfec=1');
  });

  test('gives an Opus payload type without parameters its own line after the rtpmap', () => {
    const bare = OFFER.replace('a=fmtp:111 minptime=10;useinbandfec=1\r\n', '');

    const lines = tuneOpusForMusic(bare).split('\r\n');

    const rtpmap = lines.indexOf('a=rtpmap:111 opus/48000/2');
    assert.match(lines[rtpmap + 1], /^a=fmtp:111 stereo=1;/);
  });

  test('leaves other codecs alone', () => {
    const tuned = tuneOpusForMusic(OFFER);

    assert.equal(fmtpOf(tuned, 63), 'a=fmtp:63 111/111');
    assert.ok(tuned.includes('a=rtpmap:9 G722/8000'));
  });

  test('asks for short packets once, replacing a ptime the description had', () => {
    const tuned = tuneOpusForMusic(OFFER.replace('a=sendrecv', 'a=sendrecv\r\na=ptime:20'));

    const ptimes = tuned.split('\r\n').filter((line) => line.startsWith('a=ptime:'));
    assert.deepEqual(ptimes, [`a=ptime:${PTIME_MS}`]);
  });

  test('keeps the line breaks and the final one', () => {
    const tuned = tuneOpusForMusic(OFFER);

    assert.ok(tuned.endsWith(`a=ptime:${PTIME_MS}\r\n`));
    assert.ok(!tuned.replaceAll('\r\n', '').includes('\n'));
    assert.ok(tuneOpusForMusic(OFFER.replaceAll('\r\n', '\n')).endsWith(`a=ptime:${PTIME_MS}\n`));
  });

  test('touches only audio sections', () => {
    const video = ['m=video 9 UDP/TLS/RTP/SAVPF 96', 'a=mid:1', 'a=rtpmap:96 VP8/90000', 'a=fmtp:96 x=1', '']
      .join('\r\n');

    const tuned = tuneOpusForMusic(OFFER + video);

    assert.ok(tuned.endsWith(video));
    // The ptime belongs to the audio section, before the video one starts
    assert.ok(tuned.indexOf('a=ptime:') < tuned.indexOf('m=video'));
  });

  test('is stable when applied twice', () => {
    const once = tuneOpusForMusic(OFFER);

    assert.equal(tuneOpusForMusic(once), once);
  });

  test('returns a description without Opus, or no description, as it is', () => {
    const noOpus = OFFER.replace('a=rtpmap:111 opus/48000/2\r\n', '');

    assert.equal(tuneOpusForMusic(noOpus), noOpus);
    assert.equal(tuneOpusForMusic(''), '');
    assert.equal(tuneOpusForMusic(undefined), undefined);
  });

  test('asks for every parameter of OPUS_MUSIC_PARAMS', () => {
    const fmtp = fmtpOf(tuneOpusForMusic(OFFER), 111);

    for (const [key, value] of Object.entries(OPUS_MUSIC_PARAMS)) {
      assert.ok(fmtp.includes(`${key}=${value}`), `${key}=${value}`);
    }
  });
});

describe('sessionIdOf', () => {
  test('reads the session id of the origin line', () => {
    assert.equal(sessionIdOf(OFFER), '4611731400430051336');
  });

  test('is the same whatever the version, and differs for another session', () => {
    const renegotiated = OFFER.replace('4611731400430051336 2', '4611731400430051336 3');
    const reloaded = OFFER.replace('4611731400430051336', '805551234567890123');

    assert.equal(sessionIdOf(renegotiated), sessionIdOf(OFFER));
    assert.notEqual(sessionIdOf(reloaded), sessionIdOf(OFFER));
  });

  test('is null without an origin line', () => {
    assert.equal(sessionIdOf('v=0\r\ns=-\r\n'), null);
    assert.equal(sessionIdOf(null), null);
  });
});

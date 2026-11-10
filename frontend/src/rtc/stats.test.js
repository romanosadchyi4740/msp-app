import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { summarizeStats } from './stats.js';

// What Chrome reports for a connected audio-only connection, shortened to the fields that are read
const connected = ({ localType = 'host', remoteType = 'host' } = {}) => [
  { id: 'T01', type: 'transport', selectedCandidatePairId: 'CP1' },
  { id: 'CP0', type: 'candidate-pair', localCandidateId: 'L0', remoteCandidateId: 'R1', state: 'failed' },
  {
    id: 'CP1', type: 'candidate-pair', localCandidateId: 'L1', remoteCandidateId: 'R1', state: 'succeeded',
    nominated: true, currentRoundTripTime: 0.0424,
  },
  { id: 'L0', type: 'local-candidate', candidateType: 'srflx' },
  { id: 'L1', type: 'local-candidate', candidateType: localType },
  { id: 'R1', type: 'remote-candidate', candidateType: remoteType },
  {
    id: 'IN1', type: 'inbound-rtp', kind: 'audio', jitter: 0.0061, packetsReceived: 4000, packetsLost: 12,
    bytesReceived: 640000,
  },
  { id: 'OUT1', type: 'outbound-rtp', kind: 'audio', bytesSent: 512000, codecId: 'C1' },
  { id: 'C1', type: 'codec', mimeType: 'audio/opus', sdpFmtpLine: 'minptime=10;stereo=1;maxaveragebitrate=128000' },
];

describe('summarizeStats', () => {
  test('reads the chosen route, not the other candidate pairs', () => {
    const summary = summarizeStats(connected());

    assert.equal(summary.candidateType, 'host');
    assert.equal(summary.relay, false);
    assert.equal(summary.rttMs, 42);
  });

  test('reads the received and sent audio', () => {
    const summary = summarizeStats(connected());

    assert.equal(summary.jitterMs, 6);
    assert.equal(summary.packetsReceived, 4000);
    assert.equal(summary.packetsLost, 12);
    assert.equal(summary.bytesReceived, 640000);
    assert.equal(summary.bytesSent, 512000);
    assert.equal(summary.sendFormat, 'minptime=10;stereo=1;maxaveragebitrate=128000');
  });

  test('is a relay when either side goes through TURN', () => {
    assert.equal(summarizeStats(connected({ localType: 'relay' })).relay, true);
    assert.equal(summarizeStats(connected({ remoteType: 'relay' })).relay, true);
    assert.equal(summarizeStats(connected({ localType: 'srflx', remoteType: 'prflx' })).relay, false);
  });

  test('finds the chosen pair without a transport report, as Firefox gives it', () => {
    const reports = connected({ localType: 'relay' }).filter((report) => report.type !== 'transport');

    const summary = summarizeStats(reports);

    assert.equal(summary.candidateType, 'relay');
    assert.equal(summary.rttMs, 42);
  });

  test('reads the older mediaType field for the kind', () => {
    const reports = connected().map(({ kind, ...report }) => (kind ? { ...report, mediaType: kind } : report));

    assert.equal(summarizeStats(reports).bytesReceived, 640000);
  });

  test('ignores video', () => {
    const reports = [
      { id: 'INV', type: 'inbound-rtp', kind: 'video', jitter: 0.5, packetsReceived: 9, bytesReceived: 9 },
      ...connected(),
    ];

    assert.equal(summarizeStats(reports).packetsReceived, 4000);
  });

  test('before a route is chosen there is nothing to tell', () => {
    assert.deepEqual(summarizeStats([{ id: 'T01', type: 'transport' }]), {
      candidateType: null,
      relay: false,
      rttMs: null,
      jitterMs: null,
      packetsReceived: 0,
      packetsLost: 0,
      bytesReceived: 0,
      bytesSent: 0,
      sendFormat: null,
    });
  });
});

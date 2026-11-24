/**
 * Reads what the audio mesh needs out of an RTCPeerConnection's statistics (KAN-47, KAN-48). Pure: it takes the
 * reports as a list, so it is tested without a browser.
 */

/**
 * Summarizes `[...(await pc.getStats()).values()]`:
 *
 * - `candidateType`: how this side reaches the peer, 'host', 'srflx', 'prflx' or 'relay'; null before a route is
 *   chosen.
 * - `relay`: whether the audio goes through the TURN relay on either side.
 * - `rttMs`: the round trip to the peer on the chosen route, or null if not measured yet.
 * - `jitterMs`, `packetsReceived`, `packetsLost`, `bytesReceived`: of the audio received from the peer.
 * - `bytesSent`: of the audio sent to the peer.
 * - `sendFormat`: the parameters of the codec this side sends with (`sdpFmtpLine`), or null.
 */
export function summarizeStats(reports) {
  const byId = new Map(reports.map((report) => [report.id, report]));
  const ofType = (type) => reports.filter((report) => report.type === type);

  const transport = ofType('transport').find((report) => report.selectedCandidatePairId);
  const pair = byId.get(transport?.selectedCandidatePairId)
    // Firefox has no transport report; it marks the chosen pair instead
    ?? ofType('candidate-pair').find((report) => report.selected || (report.nominated && report.state === 'succeeded'));
  const local = byId.get(pair?.localCandidateId);
  const remote = byId.get(pair?.remoteCandidateId);

  const inbound = ofType('inbound-rtp').find(isAudio);
  const outbound = ofType('outbound-rtp').find(isAudio);

  return {
    candidateType: local?.candidateType ?? null,
    relay: pair ? local?.candidateType === 'relay' || remote?.candidateType === 'relay' : false,
    rttMs: typeof pair?.currentRoundTripTime === 'number' ? Math.round(pair.currentRoundTripTime * 1000) : null,
    jitterMs: typeof inbound?.jitter === 'number' ? Math.round(inbound.jitter * 1000) : null,
    packetsReceived: inbound?.packetsReceived ?? 0,
    packetsLost: inbound?.packetsLost ?? 0,
    bytesReceived: inbound?.bytesReceived ?? 0,
    bytesSent: outbound?.bytesSent ?? 0,
    sendFormat: byId.get(outbound?.codecId)?.sdpFmtpLine ?? null,
  };
}

function isAudio(report) {
  return (report.kind ?? report.mediaType) === 'audio';
}

package com.romanosadchyi.smp.music_app.rtc.internal.dto;

import java.util.UUID;

/**
 * One WebRTC signaling message between two participants of a room (lab 7: {@code SignalMessage}); relayed only,
 * never stored or logged.
 *
 * @param roomId  set by the server from the destination, so the recipient knows which room's connection it is for
 * @param fromId  set by the server from the session's user, never taken from the client
 * @param toId    the participant it is for
 * @param payload the session description or ICE candidate as the sending browser serialized it; opaque to the server
 */
public record SignalMessage(SignalType type, UUID roomId, UUID fromId, UUID toId, String payload) {
}

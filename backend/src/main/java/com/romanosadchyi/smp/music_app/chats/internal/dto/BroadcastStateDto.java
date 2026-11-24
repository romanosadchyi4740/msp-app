package com.romanosadchyi.smp.music_app.chats.internal.dto;

import lombok.Builder;

import java.util.UUID;

/**
 * The state of a listening broadcast, sent to its topic on every change and to a single member as a snapshot
 * (KAN-109). Listeners compute the current position as
 * {@code position + (serverNow - anchorTime) / 1000} while it plays, so the server never streams positions.
 *
 * @param position   seconds into the track at {@code anchorTime}
 * @param anchorTime server epoch milliseconds when the host reported the position
 * @param serverTime server epoch milliseconds when this message was sent
 * @param version    increases with every change; listeners drop a state whose version they already applied
 */
@Builder
public record BroadcastStateDto(
        UUID broadcastId,
        UUID chatId,
        UUID hostId,
        BroadcastStatus status,
        UUID trackId,
        double position,
        boolean playing,
        long anchorTime,
        long serverTime,
        long version
) {
}

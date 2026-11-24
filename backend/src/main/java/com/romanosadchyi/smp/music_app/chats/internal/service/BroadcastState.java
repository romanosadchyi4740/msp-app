package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.dto.BroadcastStatus;
import lombok.Builder;

import java.util.UUID;

/**
 * One listening broadcast as the server keeps it (KAN-109): in memory only, since a broadcast lives as long as its
 * host listens. Replaced as a whole on every change, so a reader never sees half of one.
 *
 * @param hostSessionId the host's STOMP session that last started or updated the broadcast
 * @param hostSequence  the sequence of the host's last applied state; older ones are ignored
 * @param hostAwaySince server epoch milliseconds since that session disconnected, or null while it is connected
 * @param endedAt       server epoch milliseconds when the broadcast ended, or null while it is active
 */
@Builder(toBuilder = true)
public record BroadcastState(
        UUID broadcastId,
        UUID chatId,
        UUID hostId,
        String hostSessionId,
        long hostSequence,
        BroadcastStatus status,
        UUID trackId,
        double position,
        boolean playing,
        long anchorTime,
        long version,
        Long hostAwaySince,
        Long endedAt
) {
}

package com.romanosadchyi.smp.music_app.chats.internal.dto;

import lombok.Builder;

import java.util.UUID;

/**
 * A command to a listening broadcast. START and UPDATE carry the host's playback state: the track (none before the
 * host picks one), the position in seconds and whether it plays.
 *
 * @param sequence increases with every state the host sends. The server handles a session's messages in parallel,
 *                 so a quick play and pause can overtake each other; a state older than the last applied one is
 *                 ignored. Optional.
 */
@Builder
public record BroadcastSyncMessage(
        BroadcastCommand command,
        UUID trackId,
        Double position,
        Boolean playing,
        Long sequence
) {
}

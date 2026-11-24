package com.romanosadchyi.smp.music_app.chats.internal.dto;

/**
 * What a member sends to {@code /app/broadcast/{chatId}/{broadcastId}/sync} (KAN-109). START, UPDATE and TERMINATE
 * come from the host only (KAN-110); any member may send REQUEST_SYNC.
 */
public enum BroadcastCommand {
    /** Creates the broadcast with the sender as host, or resumes it from the host's new session after a reconnect. */
    START,
    /** The host's full playback state: track, position and whether it plays. */
    UPDATE,
    TERMINATE,
    /** Asks for the current state, which only the sender receives. */
    REQUEST_SYNC
}

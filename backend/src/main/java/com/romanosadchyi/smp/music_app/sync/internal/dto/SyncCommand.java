package com.romanosadchyi.smp.music_app.sync.internal.dto;

import java.util.UUID;

/**
 * A command to a lesson's playback (lab 7: {@code SyncCommand}); transient, never stored.
 *
 * @param argument   what the type needs (see {@link SyncCommandType}); null for none
 * @param senderId   set by the server from the session's user, never taken from the client
 * @param clientTime the sender's clock in epoch milliseconds when it sent the command; informational only
 */
public record SyncCommand(SyncCommandType type, String argument, UUID senderId, Long clientTime) {
}

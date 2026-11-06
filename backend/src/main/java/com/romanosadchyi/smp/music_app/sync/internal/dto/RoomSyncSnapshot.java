package com.romanosadchyi.smp.music_app.sync.internal.dto;

import java.util.UUID;

/**
 * Everything a participant needs to catch up with a room, sent on joining, on reconnecting and on
 * {@code REQUEST_SYNC} (FR-10). Clients never assume they saw every update.
 *
 * @param sessionId  the active lesson; null if the room has none
 * @param playback   the lesson's playback; null if the room has no active lesson
 * @param serverTime the server clock when the snapshot was taken
 */
public record RoomSyncSnapshot(UUID roomId, UUID sessionId, PlaybackStateDto playback, long serverTime) {
}

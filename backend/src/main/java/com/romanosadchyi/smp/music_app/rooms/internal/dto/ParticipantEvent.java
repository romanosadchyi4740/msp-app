package com.romanosadchyi.smp.music_app.rooms.internal.dto;

import java.util.UUID;

/**
 * A change among a room's participants, sent to {@code /topic/rooms/{roomId}/participants}.
 *
 * @param userId the participant it is about; for {@link ParticipantEventType#HOST_CHANGED} the new host
 * @param hostId the room's host after the change
 */
public record ParticipantEvent(ParticipantEventType type, UUID userId, UUID hostId) {
}

package com.romanosadchyi.smp.music_app.rooms.internal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * A change among a room's participants, sent to {@code /topic/rooms/{roomId}/participants}.
 *
 * @param userId the participant it is about; for {@link ParticipantEventType#HOST_CHANGED} the new host
 * @param hostId the room's host after the change
 * @param rttMs  for {@link ParticipantEventType#RTT} the participant's new round-trip time; null, and left out
 *               of the message, otherwise
 */
public record ParticipantEvent(ParticipantEventType type, UUID userId, UUID hostId,
                               @JsonInclude(JsonInclude.Include.NON_NULL) Integer rttMs) {

    public ParticipantEvent(ParticipantEventType type, UUID userId, UUID hostId) {
        this(type, userId, hostId, null);
    }
}

package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.QualityReport;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEvent;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEventType;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The connection quality participants report about themselves (KAN-48, FR-13): the average round-trip time of a
 * participant's audio connections, measured in the browser. It is stored with the participant and told to the room,
 * so everyone sees who has a slow connection (risk R1).
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomQualityService {

    /**
     * Anything above a minute is not a measurement of a live connection.
     */
    static final int MAX_RTT_MS = 60_000;

    RoomAccess roomAccess;
    RoomParticipantRepository participantRepository;
    ParticipantNotifier participantNotifier;

    /**
     * Stores the participant's round-trip time and, if it changed, tells the room. The room's row is not locked:
     * only this one column is written, so presence and role changes are neither held up nor overwritten.
     *
     * @throws IllegalArgumentException if the time is missing or outside 0 to {@value #MAX_RTT_MS} ms
     * @throws RoomNotFoundException    if there is no such room or the user is not in it
     */
    @Transactional
    public void report(UUID userId, UUID roomId, QualityReport report) {
        Integer rttMs = report == null ? null : report.getRttMs();
        if (rttMs == null || rttMs < 0 || rttMs > MAX_RTT_MS) {
            throw new IllegalArgumentException("rttMs must be between 0 and " + MAX_RTT_MS);
        }
        RoomParticipant participant = roomAccess.requireParticipant(userId, roomId);
        if (rttMs.equals(participant.getRttMs())) {
            return;
        }
        participantRepository.updateRttMs(participant.getId(), rttMs);
        participantNotifier.publish(roomId,
                new ParticipantEvent(ParticipantEventType.RTT, userId, participant.getRoom().getHostId(), rttMs));
    }
}

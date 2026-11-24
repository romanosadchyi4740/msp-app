package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEvent;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEventType;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Stores who has a room open (FR-10) and tells the room. {@link RoomSubscriptionTracker} decides when someone comes
 * online or goes offline; a user who is no longer a participant is ignored. Every change locks the room's row first,
 * like role changes, so a hand-over that reads a participant cannot overwrite presence stored meanwhile.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomPresenceService {

    RoomRepository roomRepository;
    RoomParticipantRepository participantRepository;
    ParticipantNotifier participantNotifier;
    Clock clock;

    @Transactional
    public void markOnline(UUID roomId, UUID userId) {
        roomRepository.findByIdForUpdate(roomId);
        participantRepository.findByRoomIdAndUserId(roomId, userId)
                .filter(participant -> !participant.isOnline())
                .ifPresent(participant -> {
                    participant.setOnline(true);
                    participant.setOfflineSince(null);
                    participantRepository.save(participant);
                    publish(participant, ParticipantEventType.ONLINE);
                });
    }

    @Transactional
    public void markOffline(UUID roomId, UUID userId) {
        roomRepository.findByIdForUpdate(roomId);
        participantRepository.findByRoomIdAndUserId(roomId, userId)
                .filter(RoomParticipant::isOnline)
                .ifPresent(participant -> {
                    participant.setOnline(false);
                    participant.setOfflineSince(clock.instant());
                    participantRepository.save(participant);
                    publish(participant, ParticipantEventType.OFFLINE);
                });
    }

    /**
     * Presence is kept per WebSocket session in memory, and no session survives a restart, so on start everyone
     * who was online goes offline from now; a host gets the usual time to reconnect before the role moves on.
     * {@link RoomPresenceReset} calls it before the web server accepts connections.
     */
    @Transactional
    public void markAllOffline() {
        int count = participantRepository.markAllOffline(clock.instant());
        if (count > 0) {
            log.info("Marked {} room participants offline after start", count);
        }
    }

    private void publish(RoomParticipant participant, ParticipantEventType type) {
        participantNotifier.publish(participant.getRoom().getId(),
                new ParticipantEvent(type, participant.getUserId(), participant.getRoom().getHostId()));
    }
}

package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.external.LessonPlayback;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEvent;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEventType;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.LessonSessionRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Passing the host role within a room (FR-11): by the host's choice, when the host leaves, or when the host stays
 * away. Callers hold the room's lock and have checked who may make the change, so every method runs only inside the
 * caller's transaction.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Transactional(propagation = Propagation.MANDATORY)
public class HostHandover {

    RoomRepository roomRepository;
    RoomParticipantRepository participantRepository;
    LessonSessionRepository lessonRepository;
    LessonPlayback lessonPlayback;
    ParticipantNotifier participantNotifier;
    Clock clock;

    /**
     * The participant other than {@code userId} who has been in the room longest, among those online if anyone is.
     */
    public Optional<RoomParticipant> successor(UUID roomId, UUID userId) {
        return participantRepository.findFirstByRoomIdAndUserIdNotAndOnlineTrueOrderByJoinedAtAsc(roomId, userId)
                .or(() -> participantRepository.findFirstByRoomIdAndUserIdNotOrderByJoinedAtAsc(roomId, userId));
    }

    /**
     * Makes {@code successor} the host and {@code host} a participant, and tells the room. With
     * {@code pausePlayback}, playing playback of the active lesson pauses, for a change the old host did not choose.
     */
    public void transfer(Room room, RoomParticipant host, RoomParticipant successor, boolean pausePlayback) {
        // One host per room is a unique index, so the old host steps down in the database first
        host.setRole(RoomRole.PARTICIPANT);
        participantRepository.saveAndFlush(host);
        successor.setRole(RoomRole.HOST);
        if (!successor.isOnline() && successor.getOfflineSince() == null) {
            // Handed the role while never seen online: away from now, so the role moves on if they do not come
            successor.setOfflineSince(clock.instant());
        }
        participantRepository.save(successor);
        room.setHostId(successor.getUserId());
        roomRepository.save(room);
        if (pausePlayback) {
            lessonRepository.findByRoomIdAndEndedAtIsNull(room.getId())
                    .ifPresent(lesson -> lessonPlayback.pause(room.getId(), lesson.getId()));
        }
        participantNotifier.publish(room.getId(), new ParticipantEvent(ParticipantEventType.HOST_CHANGED,
                successor.getUserId(), successor.getUserId()));
    }
}

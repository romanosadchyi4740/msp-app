package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonEndReason;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A host change the host did not choose (FR-11): the host left ({@link RoomLeavingService}) or stayed away
 * ({@link HostAwayService}). Playing playback pauses, so nobody plays on under a host who did not start it; with
 * nobody left to take over, the room closes. Callers hold the room's lock, so it runs only inside their transaction.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Transactional(propagation = Propagation.MANDATORY)
public class HostSuccession {

    RoomRepository roomRepository;
    RoomParticipantRepository participantRepository;
    HostHandover hostHandover;
    LessonLifecycle lessonLifecycle;

    /**
     * Hands the room of a leaving host to the participant who has been in it longest, among those online if anyone
     * is; if the host was the last one in, closes the room, which ends its lesson.
     */
    public void afterHostLeft(Room room, RoomParticipant host) {
        participantRepository.findFirstByRoomIdAndUserIdNotAndOnlineTrueOrderByJoinedAtAsc(room.getId(),
                        host.getUserId())
                .or(() -> participantRepository.findFirstByRoomIdAndUserIdNotOrderByJoinedAtAsc(room.getId(),
                        host.getUserId()))
                .ifPresentOrElse(successor -> handOver(room, host, successor), () -> close(room));
    }

    /**
     * Makes {@code successor} the host and pauses playing playback of the active lesson.
     */
    public void handOver(Room room, RoomParticipant host, RoomParticipant successor) {
        hostHandover.transfer(room, host, successor);
        lessonLifecycle.pauseActive(room.getId());
    }

    private void close(Room room) {
        if (room.getStatus() != RoomStatus.CLOSED) {
            room.setStatus(RoomStatus.CLOSED);
            roomRepository.save(room);
            lessonLifecycle.endActive(room.getId(), LessonEndReason.ROOM_CLOSED);
        }
    }
}

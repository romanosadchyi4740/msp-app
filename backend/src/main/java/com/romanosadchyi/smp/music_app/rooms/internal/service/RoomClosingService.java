package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonEndReason;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.mapping.RoomMappingService;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Closing a room, which ends its active lesson.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomClosingService {

    RoomRepository roomRepository;
    RoomParticipantRepository participantRepository;
    RoomAccess roomAccess;
    LessonLifecycle lessonLifecycle;
    RoomMappingService roomMappingService;

    /**
     * Closes the room, so nobody can join it any more, and ends its active lesson. Closing a closed room changes
     * nothing. The room's row stays locked until the commit, so a lesson cannot start meanwhile.
     *
     * @throws RoomNotFoundException      if there is no such room or the user is not in it
     * @throws UserNotAuthorizedException if the user is not the host
     */
    @Transactional
    public RoomResponse close(UUID userId, UUID roomId) {
        Room room = roomAccess.lockAsHost(userId, roomId, "Only the host can close the room");
        if (room.getStatus() != RoomStatus.CLOSED) {
            room.setStatus(RoomStatus.CLOSED);
            roomRepository.save(room);
            lessonLifecycle.endActive(roomId, LessonEndReason.ROOM_CLOSED);
            log.info("User {} closed room {}", userId, roomId);
        }
        return mapRoom(room, userId);
    }

    private RoomResponse mapRoom(Room room, UUID viewerId) {
        return roomMappingService.map(room, participantRepository.findAllByRoomIdOrderByJoinedAtAsc(room.getId()),
                viewerId);
    }
}

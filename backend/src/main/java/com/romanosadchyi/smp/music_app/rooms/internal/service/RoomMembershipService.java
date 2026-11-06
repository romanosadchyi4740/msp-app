package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.HostTransferRequest;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEvent;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEventType;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonEndReason;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomClosedException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomParticipantNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.mapping.RoomMappingService;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.shared.util.AfterCommit;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Who is in a room and who hosts it (KAN-36, FR-11): the host hands the role on, and anyone leaves. Every change
 * locks the room's row first, like lesson changes, so it cannot interleave with them or with the away-host check.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomMembershipService {

    RoomRepository roomRepository;
    RoomParticipantRepository participantRepository;
    RoomAccess roomAccess;
    HostHandover hostHandover;
    LessonLifecycle lessonLifecycle;
    ParticipantNotifier participantNotifier;
    RoomSubscriptionTracker roomSubscriptionTracker;
    RoomMappingService roomMappingService;

    /**
     * Makes another participant the host; playback goes on as it is.
     *
     * @throws IllegalArgumentException         if no new host is named, or it is the host already
     * @throws RoomNotFoundException            if there is no such room or the user is not in it
     * @throws UserNotAuthorizedException       if the user is not the host
     * @throws RoomClosedException              if the room is closed
     * @throws RoomParticipantNotFoundException if the new host is not in the room
     */
    @Transactional
    public RoomResponse transferHost(UUID userId, UUID roomId, HostTransferRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("The new host is required");
        }
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found"));
        RoomParticipant host = roomAccess.requireHost(userId, roomId, "Only the host can hand over the room");
        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new RoomClosedException("The room is closed");
        }
        if (request.getUserId().equals(userId)) {
            throw new IllegalArgumentException("You are the host already");
        }
        RoomParticipant successor = participantRepository.findByRoomIdAndUserId(roomId, request.getUserId())
                .orElseThrow(() -> new RoomParticipantNotFoundException("The new host is not in the room"));

        hostHandover.transfer(room, host, successor, false);
        log.info("User {} handed room {} to user {}", userId, roomId, successor.getUserId());
        return roomMappingService.map(room, participantRepository.findAllByRoomIdOrderByJoinedAtAsc(roomId), userId);
    }

    /**
     * Frees the user's place. A leaving host of an open room hands the role on and playback pauses; the last one in
     * the room closes it, which ends its lesson. In a closed room nobody needs a host. After the commit the user's
     * subscriptions to the room's topics end.
     *
     * @throws RoomNotFoundException if there is no such room or the user is not in it
     */
    @Transactional
    public void leave(UUID userId, UUID roomId) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found"));
        RoomParticipant leaving = roomAccess.requireParticipant(userId, roomId);
        if (leaving.getRole() == RoomRole.HOST && room.getStatus() != RoomStatus.CLOSED) {
            hostHandover.successor(roomId, userId).ifPresentOrElse(
                    successor -> hostHandover.transfer(room, leaving, successor, true),
                    () -> close(room));
        }
        participantRepository.delete(leaving);
        participantRepository.flush();
        participantNotifier.publish(roomId, new ParticipantEvent(ParticipantEventType.LEFT, userId,
                room.getHostId()));
        AfterCommit.run(() -> roomSubscriptionTracker.revoke(roomId, userId));
        log.info("User {} left room {}", userId, roomId);
    }

    private void close(Room room) {
        if (room.getStatus() != RoomStatus.CLOSED) {
            room.setStatus(RoomStatus.CLOSED);
            roomRepository.save(room);
            lessonLifecycle.endActive(room.getId(), LessonEndReason.ROOM_CLOSED);
        }
    }
}

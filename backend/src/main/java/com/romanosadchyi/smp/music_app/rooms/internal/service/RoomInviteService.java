package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomPreview;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEvent;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEventType;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomClosedException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomFullException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.mapping.RoomMappingService;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Invites (FR-5): previewing and joining a room by its invite code, and replacing the code. Joins lock the room's row,
 * so the capacity holds under concurrent joins.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomInviteService {

    RoomRepository roomRepository;
    RoomParticipantRepository participantRepository;
    InviteCodes inviteCodes;
    RoomAccess roomAccess;
    RoomMappingService roomMappingService;
    ParticipantNotifier participantNotifier;

    /**
     * Replaces the invite code, so links with the old one stop working.
     *
     * @throws RoomNotFoundException      if there is no such room or the user is not in it
     * @throws UserNotAuthorizedException if the user is not the host
     * @throws RoomClosedException        if the room is closed
     */
    @Transactional
    public RoomResponse regenerateInvite(UUID userId, UUID roomId) {
        Room room = roomAccess.requireHost(userId, roomId, "Only the host can change the invite link").getRoom();
        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new RoomClosedException("The room is closed");
        }
        room.setInviteCode(inviteCodes.newCode());
        roomRepository.save(room);
        log.info("User {} regenerated the invite code of room {}", userId, roomId);
        return mapRoom(room, userId);
    }

    /**
     * @throws RoomNotFoundException if no room has this invite code
     */
    @Transactional(readOnly = true)
    public RoomPreview getPreview(String inviteCode) {
        Room room = roomRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new RoomNotFoundException("Invite link is invalid"));
        return roomMappingService.mapPreview(room, participantRepository.countByRoomId(room.getId()));
    }

    /**
     * Adds the user to the room; a user already in it stays as they are, even when the room is full. A closed room
     * rejects every join, its members' too; they still read it by id. The room's row stays locked until the
     * transaction commits, so concurrent joins never exceed the capacity.
     *
     * @throws UserNotFoundException if the account is deleted
     * @throws RoomNotFoundException if no room has this invite code
     * @throws RoomClosedException   if the room is closed
     * @throws RoomFullException     if the room has no free place
     */
    @Transactional
    public RoomResponse join(UUID userId, String inviteCode) {
        roomAccess.requireUser(userId);
        Room room = roomRepository.findByInviteCodeForUpdate(inviteCode)
                .orElseThrow(() -> new RoomNotFoundException("Invite link is invalid"));
        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new RoomClosedException("The room is closed");
        }
        if (participantRepository.findByRoomIdAndUserId(room.getId(), userId).isEmpty()) {
            if (participantRepository.countByRoomId(room.getId()) >= room.getCapacity()) {
                throw new RoomFullException("The room is full");
            }
            participantRepository.save(RoomParticipant.builder()
                    .room(room)
                    .userId(userId)
                    .role(RoomRole.PARTICIPANT)
                    .build());
            participantNotifier.publish(room.getId(), new ParticipantEvent(ParticipantEventType.JOINED, userId,
                    room.getHostId()));
            log.info("User {} joined room {}", userId, room.getId());
        }
        return mapRoom(room, userId);
    }

    private RoomResponse mapRoom(Room room, UUID viewerId) {
        return roomMappingService.map(room, participantRepository.findAllByRoomIdOrderByJoinedAtAsc(room.getId()),
                viewerId);
    }
}

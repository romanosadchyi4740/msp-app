package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomCreateRequest;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomListItem;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomPreview;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomClosedException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomFullException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.mapping.RoomMappingService;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantCount;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Creating rooms, invite codes and joining (FR-5). A room is readable only by its participants; to anyone else it
 * answers as not found, so room ids reveal nothing. Joins lock the room's row, so the capacity holds under concurrent
 * joins.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomService {

    /**
     * The host and up to 4 participants, which also caps the WebRTC mesh at 4 peers.
     */
    static final int CAPACITY = 5;
    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_INVITE_CODE_ATTEMPTS = 5;

    RoomRepository roomRepository;
    RoomParticipantRepository participantRepository;
    InviteCodeGenerator inviteCodeGenerator;
    RoomMappingService roomMappingService;
    UserLookup userLookup;

    /**
     * @throws UserNotFoundException    if the account is deleted
     * @throws IllegalArgumentException if the name is missing or too long
     */
    @Transactional
    public RoomResponse create(UUID hostId, RoomCreateRequest request) {
        requireUser(hostId);
        String name = request.getName() != null ? request.getName().strip() : "";
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Room name is required");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Room name can be at most " + MAX_NAME_LENGTH + " characters long");
        }

        Room room = roomRepository.save(Room.builder()
                .hostId(hostId)
                .name(name)
                .inviteCode(newInviteCode())
                .capacity(CAPACITY)
                .status(RoomStatus.OPEN)
                .build());
        participantRepository.save(RoomParticipant.builder()
                .room(room)
                .userId(hostId)
                .role(RoomRole.HOST)
                .build());
        log.info("User {} created room {}", hostId, room.getId());
        return mapRoom(room, hostId);
    }

    /**
     * Replaces the invite code, so links with the old one stop working.
     *
     * @throws RoomNotFoundException      if there is no such room or the user is not in it
     * @throws UserNotAuthorizedException if the user is not the host
     * @throws RoomClosedException        if the room is closed
     */
    @Transactional
    public RoomResponse regenerateInvite(UUID userId, UUID roomId) {
        RoomParticipant participant = getParticipant(userId, roomId);
        if (participant.getRole() != RoomRole.HOST) {
            throw new UserNotAuthorizedException("Only the host can change the invite link");
        }
        Room room = participant.getRoom();
        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new RoomClosedException("The room is closed");
        }
        room.setInviteCode(newInviteCode());
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
        requireUser(userId);
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
            log.info("User {} joined room {}", userId, room.getId());
        }
        return mapRoom(room, userId);
    }

    /**
     * @throws RoomNotFoundException if there is no such room or the user is not in it
     */
    @Transactional(readOnly = true)
    public RoomResponse getRoom(UUID userId, UUID roomId) {
        return mapRoom(getParticipant(userId, roomId).getRoom(), userId);
    }

    @Transactional(readOnly = true)
    public List<RoomListItem> getMyRooms(UUID userId) {
        List<RoomParticipant> memberships = participantRepository.findAllWithRoomByUserId(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<UUID> roomIds = memberships.stream().map(membership -> membership.getRoom().getId()).toList();
        Map<UUID, Long> counts = participantRepository.countByRoomIds(roomIds).stream()
                .collect(Collectors.toMap(RoomParticipantCount::getRoomId, RoomParticipantCount::getCount));
        return roomMappingService.mapAll(memberships, counts);
    }

    /**
     * A code no room has yet; the unique constraint still rejects one taken by a concurrent transaction.
     */
    private String newInviteCode() {
        for (int attempt = 0; attempt < MAX_INVITE_CODE_ATTEMPTS; attempt++) {
            String code = inviteCodeGenerator.generate();
            if (!roomRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a free invite code");
    }

    /**
     * A deleted account can still hold an unexpired token, but must not get new rooms or places.
     */
    private void requireUser(UUID userId) {
        if (!userLookup.exists(userId)) {
            throw new UserNotFoundException("User not found: " + userId);
        }
    }

    private RoomParticipant getParticipant(UUID userId, UUID roomId) {
        return participantRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found"));
    }

    private RoomResponse mapRoom(Room room, UUID viewerId) {
        return roomMappingService.map(room, participantRepository.findAllByRoomIdOrderByJoinedAtAsc(room.getId()),
                viewerId);
    }
}

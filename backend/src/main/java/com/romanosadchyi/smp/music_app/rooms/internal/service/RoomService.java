package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomCreateRequest;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomListItem;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.mapping.RoomMappingService;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantCount;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
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
 * Creating rooms and reading them. A room is readable only by its participants; to anyone else it answers as not
 * found, so room ids reveal nothing.
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

    RoomRepository roomRepository;
    RoomParticipantRepository participantRepository;
    InviteCodes inviteCodes;
    RoomAccess roomAccess;
    RoomMappingService roomMappingService;

    /**
     * @throws UserNotFoundException    if the account is deleted
     * @throws IllegalArgumentException if the name is missing or too long
     */
    @Transactional
    public RoomResponse create(UUID hostId, RoomCreateRequest request) {
        roomAccess.requireUser(hostId);
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
                .inviteCode(inviteCodes.newCode())
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
     * @throws RoomNotFoundException if there is no such room or the user is not in it
     */
    @Transactional(readOnly = true)
    public RoomResponse getRoom(UUID userId, UUID roomId) {
        return mapRoom(roomAccess.requireParticipant(userId, roomId).getRoom(), userId);
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

    private RoomResponse mapRoom(Room room, UUID viewerId) {
        return roomMappingService.map(room, participantRepository.findAllByRoomIdOrderByJoinedAtAsc(room.getId()),
                viewerId);
    }
}

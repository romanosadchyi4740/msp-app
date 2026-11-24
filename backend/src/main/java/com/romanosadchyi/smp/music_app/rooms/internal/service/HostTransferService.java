package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.HostTransferRequest;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomClosedException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomParticipantNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.mapping.RoomMappingService;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The host hands the role to a participant of their choice (KAN-36, FR-11). The room's row is locked first, like for
 * lesson changes, so the transfer cannot interleave with them or with the away-host check.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class HostTransferService {

    RoomAccess roomAccess;
    RoomParticipantRepository participantRepository;
    HostHandover hostHandover;
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
        Room room = roomAccess.lock(roomId);
        RoomParticipant host = roomAccess.requireHost(userId, roomId, "Only the host can hand over the room");
        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new RoomClosedException("The room is closed");
        }
        if (request.getUserId().equals(userId)) {
            throw new IllegalArgumentException("You are the host already");
        }
        RoomParticipant successor = participantRepository.findByRoomIdAndUserId(roomId, request.getUserId())
                .orElseThrow(() -> new RoomParticipantNotFoundException("The new host is not in the room"));

        hostHandover.transfer(room, host, successor);
        log.info("User {} handed room {} to user {}", userId, roomId, successor.getUserId());
        return roomMappingService.map(room, participantRepository.findAllByRoomIdOrderByJoinedAtAsc(roomId), userId);
    }
}

package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Who may do what in a room. A room the user is not in answers as not found, so room ids reveal nothing.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomAccess {

    RoomRepository roomRepository;
    RoomParticipantRepository participantRepository;
    UserLookup userLookup;

    /**
     * A deleted account can still hold an unexpired token, but must not get new rooms or places.
     *
     * @throws UserNotFoundException if the account is deleted
     */
    public void requireUser(UUID userId) {
        if (!userLookup.exists(userId)) {
            throw new UserNotFoundException("User not found: " + userId);
        }
    }

    /**
     * @throws RoomNotFoundException if there is no such room or the user is not in it
     */
    public RoomParticipant requireParticipant(UUID userId, UUID roomId) {
        return participantRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found"));
    }

    /**
     * @throws RoomNotFoundException      if there is no such room or the user is not in it
     * @throws UserNotAuthorizedException with {@code notHostMessage} if the user is not the host
     */
    public RoomParticipant requireHost(UUID userId, UUID roomId, String notHostMessage) {
        RoomParticipant participant = requireParticipant(userId, roomId);
        if (participant.getRole() != RoomRole.HOST) {
            throw new UserNotAuthorizedException(notHostMessage);
        }
        return participant;
    }

    /**
     * Locks the room's row until the transaction ends, then checks the user is its host, so the check sees the room
     * as it is under the lock.
     *
     * @throws RoomNotFoundException      if there is no such room or the user is not in it
     * @throws UserNotAuthorizedException with {@code notHostMessage} if the user is not the host
     */
    public Room lockAsHost(UUID userId, UUID roomId, String notHostMessage) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found"));
        requireHost(userId, roomId, notHostMessage);
        return room;
    }
}

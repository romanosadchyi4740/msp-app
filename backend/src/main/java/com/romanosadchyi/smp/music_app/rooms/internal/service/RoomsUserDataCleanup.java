package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import com.romanosadchyi.smp.music_app.users.external.UserDataCleanup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * On account deletion: deletes the user's places in rooms and the rooms they host, whose other participants the
 * database removes with the room.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class RoomsUserDataCleanup implements UserDataCleanup {

    RoomRepository roomRepository;
    RoomParticipantRepository participantRepository;

    @Override
    public void deleteUserData(UUID userId) {
        participantRepository.deleteAll(participantRepository.findAllByUserId(userId));
        participantRepository.flush();
        roomRepository.deleteAll(roomRepository.findAllByHostId(userId));
        roomRepository.flush();
    }
}

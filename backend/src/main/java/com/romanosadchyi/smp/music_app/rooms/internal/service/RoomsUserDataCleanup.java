package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomMessageRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import com.romanosadchyi.smp.music_app.shared.util.AfterCommit;
import com.romanosadchyi.smp.music_app.users.external.UserDataCleanup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * On account deletion: deletes the user's chat messages in every room (KAN-51), their places in rooms and the rooms
 * they host, whose other participants and messages the database removes with the room. After the commit, the user's
 * open STOMP sessions stop getting those rooms' topics, as when leaving.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class RoomsUserDataCleanup implements UserDataCleanup {

    RoomRepository roomRepository;
    RoomParticipantRepository participantRepository;
    RoomMessageRepository messageRepository;
    RoomSubscriptionTracker roomSubscriptionTracker;

    @Override
    public void deleteUserData(UUID userId) {
        messageRepository.deleteAllByAuthorId(userId);
        List<RoomParticipant> places = participantRepository.findAllByUserId(userId);
        List<UUID> roomIds = places.stream().map(place -> place.getRoom().getId()).toList();
        participantRepository.deleteAll(places);
        participantRepository.flush();
        AfterCommit.run(() -> roomIds.forEach(roomId -> roomSubscriptionTracker.revoke(roomId, userId)));
        roomRepository.deleteAll(roomRepository.findAllByHostId(userId));
        roomRepository.flush();
    }
}

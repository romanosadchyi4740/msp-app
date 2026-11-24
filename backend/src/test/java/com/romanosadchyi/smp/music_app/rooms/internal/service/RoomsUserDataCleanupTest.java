package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomMessageRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomsUserDataCleanupTest {

    @Mock
    RoomRepository roomRepository;

    @Mock
    RoomParticipantRepository participantRepository;

    @Mock
    RoomMessageRepository messageRepository;

    @Mock
    RoomSubscriptionTracker roomSubscriptionTracker;

    @InjectMocks
    RoomsUserDataCleanup cleanup;

    @Test
    void deleteUserData_DeletesTheUsersMessagesPlacesAndHostedRoomsThenEndsTheirSubscriptions() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        List<RoomParticipant> places = List.of(RoomParticipant.builder()
                .room(Room.builder().id(roomId).build())
                .userId(userId)
                .build());
        List<Room> hosted = List.of(Room.builder().hostId(userId).build());
        when(participantRepository.findAllByUserId(userId)).thenReturn(places);
        when(roomRepository.findAllByHostId(userId)).thenReturn(hosted);

        cleanup.deleteUserData(userId);

        InOrder order = inOrder(messageRepository, participantRepository, roomSubscriptionTracker, roomRepository);
        order.verify(messageRepository).deleteAllByAuthorId(userId);
        order.verify(participantRepository).deleteAll(places);
        order.verify(participantRepository).flush();
        // Outside a transaction AfterCommit runs at once
        order.verify(roomSubscriptionTracker).revoke(roomId, userId);
        order.verify(roomRepository).deleteAll(hosted);
        order.verify(roomRepository).flush();
    }
}

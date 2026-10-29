package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
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

    @InjectMocks
    RoomsUserDataCleanup cleanup;

    @Test
    void deleteUserData_DeletesTheUsersPlacesAndHostedRooms() {
        UUID userId = UUID.randomUUID();
        List<RoomParticipant> places = List.of(RoomParticipant.builder().userId(userId).build());
        List<Room> hosted = List.of(Room.builder().hostId(userId).build());
        when(participantRepository.findAllByUserId(userId)).thenReturn(places);
        when(roomRepository.findAllByHostId(userId)).thenReturn(hosted);

        cleanup.deleteUserData(userId);

        InOrder order = inOrder(participantRepository, roomRepository);
        order.verify(participantRepository).deleteAll(places);
        order.verify(participantRepository).flush();
        order.verify(roomRepository).deleteAll(hosted);
        order.verify(roomRepository).flush();
    }
}

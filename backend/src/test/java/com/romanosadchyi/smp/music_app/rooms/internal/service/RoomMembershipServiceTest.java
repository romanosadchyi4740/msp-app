package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomMembershipServiceTest {

    @Mock
    RoomParticipantRepository participantRepository;

    @InjectMocks
    RoomMembershipService roomMembership;

    UUID roomId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    @Test
    void isParticipantOfOpenRoom_UserInAnOpenRoom_IsTrue() {
        when(participantRepository.existsByRoomIdAndUserIdAndRoomStatus(roomId, userId, RoomStatus.OPEN))
                .thenReturn(true);

        assertThat(roomMembership.isParticipantOfOpenRoom(roomId, userId)).isTrue();
    }

    @Test
    void isParticipantOfOpenRoom_NotInTheRoomOrRoomClosedOrMissing_IsFalse() {
        when(participantRepository.existsByRoomIdAndUserIdAndRoomStatus(roomId, userId, RoomStatus.OPEN))
                .thenReturn(false);

        assertThat(roomMembership.isParticipantOfOpenRoom(roomId, userId)).isFalse();
    }
}

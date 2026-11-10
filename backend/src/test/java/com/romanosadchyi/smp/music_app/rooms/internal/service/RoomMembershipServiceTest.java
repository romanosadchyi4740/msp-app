package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
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
    void isParticipant_UserInTheRoom_IsTrue() {
        when(participantRepository.findByRoomIdAndUserId(roomId, userId))
                .thenReturn(Optional.of(new RoomParticipant()));

        assertThat(roomMembership.isParticipant(roomId, userId)).isTrue();
    }

    @Test
    void isParticipant_UserNotInTheRoomOrNoSuchRoom_IsFalse() {
        when(participantRepository.findByRoomIdAndUserId(roomId, userId)).thenReturn(Optional.empty());

        assertThat(roomMembership.isParticipant(roomId, userId)).isFalse();
    }
}

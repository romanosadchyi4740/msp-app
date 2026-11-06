package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomStompGuardTest {

    @Mock
    RoomParticipantRepository participantRepository;

    @InjectMocks
    RoomStompGuard guard;

    UUID roomId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    @ParameterizedTest
    @ValueSource(strings = {"/topic/rooms/x/state", "/app/rooms/x/command", "/topic/rooms", "/topic/rooms/",
            "/app/rooms/x/snapshot", "/topic/rooms/x"})
    void guards_EveryRoomDestination(String destination) {
        assertThat(guard.guards(destination)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/broadcast/x", "/user/queue/rooms/x/snapshot", "/app/chat", "/topic/roomsx/y"})
    void guards_NotOtherDestinations(String destination) {
        assertThat(guard.guards(destination)).isFalse();
    }

    @Test
    void mayAccess_Participant() {
        when(participantRepository.findByRoomIdAndUserId(roomId, userId))
                .thenReturn(Optional.of(new RoomParticipant()));

        assertThat(guard.mayAccess(userId, "/topic/rooms/" + roomId + "/state")).isTrue();
        assertThat(guard.mayAccess(userId, "/app/rooms/" + roomId + "/command")).isTrue();
    }

    @Test
    void mayAccess_NonParticipant_IsDenied() {
        when(participantRepository.findByRoomIdAndUserId(roomId, userId)).thenReturn(Optional.empty());

        assertThat(guard.mayAccess(userId, "/topic/rooms/" + roomId + "/state")).isFalse();
    }

    @Test
    void mayAccess_MalformedOrNonCanonicalId_IsDenied() {
        assertThat(guard.mayAccess(userId, "/topic/rooms/not-a-uuid/state")).isFalse();
        assertThat(guard.mayAccess(userId, "/topic/rooms/" + roomId.toString().toUpperCase() + "/state")).isFalse();
        assertThat(guard.mayAccess(userId, "/topic/rooms/1-1-1-1-1/state")).isFalse();
        assertThat(guard.mayAccess(userId, "/topic/rooms")).isFalse();
        verifyNoInteractions(participantRepository);
    }

    @Test
    void roomIdOf_ReadsTheCanonicalId() {
        assertThat(RoomStompGuard.roomIdOf("/app/rooms/" + roomId + "/snapshot")).contains(roomId);
        assertThat(RoomStompGuard.roomIdOf("/topic/rooms/" + roomId)).contains(roomId);
        assertThat(RoomStompGuard.roomIdOf("/topic/rooms//state")).isEmpty();
    }

    @Test
    void mayAccess_LooksUpOnlyTheNamedRoom() {
        when(participantRepository.findByRoomIdAndUserId(any(), any())).thenReturn(Optional.empty());

        guard.mayAccess(userId, "/topic/rooms/" + roomId + "/participants");

        verify(participantRepository).findByRoomIdAndUserId(roomId, userId);
    }
}

package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.entity.Chat;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatStatus;
import com.romanosadchyi.smp.music_app.chats.internal.repository.ChatRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BroadcastStompGuardTest {

    @Mock
    ChatRepository chatRepository;

    @InjectMocks
    BroadcastStompGuard guard;

    UUID chatId = UUID.randomUUID();
    UUID broadcastId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    @ParameterizedTest
    @ValueSource(strings = {"/topic/broadcast/x", "/topic/broadcast/x/y", "/app/broadcast/x/sync",
            "/app/broadcast/x/y/sync", "/topic/broadcast"})
    void guards_EveryBroadcastDestination(String destination) {
        assertThat(guard.guards(destination)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/rooms/x/state", "/app/chat", "/user/queue/messages", "/topic/broadcastx"})
    void guards_NotOtherDestinations(String destination) {
        assertThat(guard.guards(destination)).isFalse();
    }

    @Test
    void mayAccess_ChatMember() {
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat(false, ChatStatus.ACCEPTED)));

        assertThat(guard.mayAccess(userId, topic())).isTrue();
        assertThat(guard.mayAccess(userId, "/app/broadcast/" + chatId + "/" + broadcastId + "/sync")).isTrue();
    }

    @Test
    void mayAccess_PendingInvitee_MayListen() {
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat(true, ChatStatus.PENDING)));

        assertThat(guard.mayAccess(userId, topic())).isTrue();
    }

    @Test
    void mayAccess_NonMember_IsDenied() {
        Chat chat = Chat.builder().id(chatId).build();
        ChatMemberships.add(chat, UUID.randomUUID(), ChatStatus.ACCEPTED, true);
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));

        assertThat(guard.mayAccess(userId, topic())).isFalse();
    }

    @Test
    void mayAccess_DeclinedGroupMember_IsDenied() {
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat(true, ChatStatus.REJECTED)));

        assertThat(guard.mayAccess(userId, topic())).isFalse();
    }

    @Test
    void mayAccess_UnknownChat_IsDenied() {
        when(chatRepository.findById(chatId)).thenReturn(Optional.empty());

        assertThat(guard.mayAccess(userId, topic())).isFalse();
    }

    @Test
    void mayAccess_OldOrMalformedDestinations_AreDenied() {
        assertThat(guard.mayAccess(userId, "/topic/broadcast/" + broadcastId)).isFalse();
        assertThat(guard.mayAccess(userId, "/app/broadcast/" + broadcastId + "/sync")).isFalse();
        assertThat(guard.mayAccess(userId, "/topic/broadcast/" + chatId + "/not-a-uuid")).isFalse();
        assertThat(guard.mayAccess(userId, "/topic/broadcast/" + chatId.toString().toUpperCase() + "/"
                + broadcastId)).isFalse();
        assertThat(guard.mayAccess(userId, "/topic/broadcast/" + chatId + "/" + broadcastId + "/extra")).isFalse();
        verifyNoInteractions(chatRepository);
    }

    private String topic() {
        return "/topic/broadcast/" + chatId + "/" + broadcastId;
    }

    private Chat chat(boolean group, ChatStatus status) {
        Chat chat = Chat.builder().id(chatId).isGroup(group).build();
        ChatMemberships.add(chat, userId, status, false);
        return chat;
    }
}

package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageRequestDto;
import com.romanosadchyi.smp.music_app.chats.internal.entity.Chat;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatStatus;
import com.romanosadchyi.smp.music_app.chats.internal.exception.ChatNotFoundException;
import com.romanosadchyi.smp.music_app.chats.internal.repository.ChatMembershipRepository;
import com.romanosadchyi.smp.music_app.chats.internal.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageChatResolverTest {

    @Mock
    ChatRepository chatRepository;

    @Mock
    ChatMembershipRepository chatMembershipRepository;

    @Mock
    DirectChatResolver directChatResolver;

    @Mock
    UserLookup userLookup;

    @InjectMocks
    MessageChatResolver resolver;

    final UUID senderId = UUID.randomUUID();
    final Chat chat = Chat.builder().id(UUID.randomUUID()).build();

    @BeforeEach
    void senderExists() {
        lenient().when(userLookup.exists(senderId)).thenReturn(true);
    }

    @Test
    void byChatId_SenderIsMember_ReturnsChat() {
        ChatMemberships.add(chat, senderId, ChatStatus.ACCEPTED, false);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));

        assertThat(resolver.resolve(request(chat.getId(), null), senderId)).isSameAs(chat);
        verifyNoInteractions(chatMembershipRepository);
    }

    @Test
    void byRecipient_UsesTheDirectChat() {
        UUID recipient = UUID.randomUUID();
        ChatMemberships.add(chat, senderId, ChatStatus.ACCEPTED, true);
        when(directChatResolver.getOrCreate(senderId, recipient)).thenReturn(chat);

        assertThat(resolver.resolve(request(null, recipient), senderId)).isSameAs(chat);
    }

    @Test
    void rejectedSender_ReopensTheChatAsAdmin() {
        ChatMembership membership = ChatMemberships.add(chat, senderId, ChatStatus.REJECTED, false);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));

        resolver.resolve(request(chat.getId(), null), senderId);

        assertThat(membership.getStatus()).isEqualTo(ChatStatus.PENDING);
        assertThat(membership.isAdmin()).isTrue();
        verify(chatMembershipRepository).save(membership);
    }

    @Test
    void senderNotInChat_IsRejected() {
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));

        assertThatThrownBy(() -> resolver.resolve(request(chat.getId(), null), senderId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noChatAndNoRecipient_IsRejected() {
        assertThatThrownBy(() -> resolver.resolve(request(null, null), senderId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownChat_Throws() {
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(request(chat.getId(), null), senderId))
                .isInstanceOf(ChatNotFoundException.class);
    }

    @Test
    void unknownSender_Throws() {
        UUID stranger = UUID.randomUUID();
        when(userLookup.exists(stranger)).thenReturn(false);

        assertThatThrownBy(() -> resolver.resolve(request(chat.getId(), null), stranger))
                .isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(chatRepository);
    }

    private static ChatMessageRequestDto request(UUID chatId, UUID recipientId) {
        return new ChatMessageRequestDto(chatId, recipientId, "Hello", null, null, null);
    }
}

package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.entity.Chat;
import com.romanosadchyi.smp.music_app.model.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.model.enums.ChatStatus;
import com.romanosadchyi.smp.music_app.repository.ChatMembershipRepository;
import com.romanosadchyi.smp.music_app.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectChatResolverTest {

    @Mock
    ChatRepository chatRepository;

    @Mock
    ChatMembershipRepository chatMembershipRepository;

    @Mock
    UserLookup userLookup;

    @InjectMocks
    DirectChatResolver resolver;

    final UUID initiator = UUID.randomUUID();
    final UUID recipient = UUID.randomUUID();

    @Test
    void newChat_InitiatorIsAcceptedAdminAndRecipientPending() {
        when(chatRepository.findChatBetweenUsers(initiator, recipient)).thenReturn(Optional.empty());
        when(userLookup.exists(any())).thenReturn(true);
        when(chatRepository.save(any(Chat.class))).thenAnswer(i -> i.getArgument(0));

        Chat chat = resolver.getOrCreate(initiator, recipient);

        assertThat(chat.getIsGroup()).isFalse();
        assertMembership(chat, initiator, ChatStatus.ACCEPTED, true);
        assertMembership(chat, recipient, ChatStatus.PENDING, false);
    }

    @Test
    void existingChat_RejectedMembershipsAreReopened() {
        Chat chat = Chat.builder().id(UUID.randomUUID()).build();
        chat.addMember(initiator, ChatStatus.REJECTED, false);
        chat.addMember(recipient, ChatStatus.REJECTED, true);
        when(chatRepository.findChatBetweenUsers(initiator, recipient)).thenReturn(Optional.of(chat));

        assertThat(resolver.getOrCreate(initiator, recipient)).isSameAs(chat);

        assertMembership(chat, initiator, ChatStatus.ACCEPTED, true);
        assertMembership(chat, recipient, ChatStatus.PENDING, false);
        verify(chatRepository, never()).save(any());
    }

    @Test
    void existingChat_AcceptedMembershipsAreLeftAlone() {
        Chat chat = Chat.builder().id(UUID.randomUUID()).build();
        chat.addMember(initiator, ChatStatus.ACCEPTED, false);
        chat.addMember(recipient, ChatStatus.ACCEPTED, true);
        when(chatRepository.findChatBetweenUsers(initiator, recipient)).thenReturn(Optional.of(chat));

        resolver.getOrCreate(initiator, recipient);

        assertMembership(chat, initiator, ChatStatus.ACCEPTED, false);
        assertMembership(chat, recipient, ChatStatus.ACCEPTED, true);
        verify(chatMembershipRepository, never()).save(any());
    }

    @Test
    void newChat_UnknownRecipient_Throws() {
        when(chatRepository.findChatBetweenUsers(initiator, recipient)).thenReturn(Optional.empty());
        when(userLookup.exists(initiator)).thenReturn(true);
        when(userLookup.exists(recipient)).thenReturn(false);

        assertThatThrownBy(() -> resolver.getOrCreate(initiator, recipient)).isInstanceOf(UserNotFoundException.class);
        verify(chatRepository, never()).save(any());
    }

    private static void assertMembership(Chat chat, UUID userId, ChatStatus status, boolean admin) {
        ChatMembership membership = chat.membershipOf(userId).orElseThrow();
        assertThat(membership.getStatus()).isEqualTo(status);
        assertThat(membership.isAdmin()).isEqualTo(admin);
    }
}

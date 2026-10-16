package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.entity.Chat;
import com.romanosadchyi.smp.music_app.model.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.model.enums.ChatStatus;
import com.romanosadchyi.smp.music_app.repository.ChatMembershipRepository;
import com.romanosadchyi.smp.music_app.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The one-on-one chat between two users, created when it does not exist yet. The initiator is the chat's admin
 * and the other user's membership starts as pending; reaching out again reopens a chat either side had rejected.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DirectChatResolver {

    ChatRepository chatRepository;
    ChatMembershipRepository chatMembershipRepository;
    UserLookup userLookup;

    public Chat getOrCreate(UUID initiatorId, UUID recipientId) {
        return chatRepository.findChatBetweenUsers(initiatorId, recipientId)
                .map(chat -> reopen(chat, initiatorId, recipientId))
                .orElseGet(() -> create(initiatorId, recipientId));
    }

    private Chat reopen(Chat chat, UUID initiatorId, UUID recipientId) {
        chat.membershipOf(initiatorId)
                .filter(m -> m.getStatus() == ChatStatus.REJECTED)
                .ifPresent(m -> reset(m, ChatStatus.ACCEPTED, true));
        chat.membershipOf(recipientId)
                .filter(m -> m.getStatus() == ChatStatus.REJECTED)
                .ifPresent(m -> reset(m, ChatStatus.PENDING, false));
        return chat;
    }

    private void reset(ChatMembership membership, ChatStatus status, boolean admin) {
        membership.setStatus(status);
        membership.setAdmin(admin);
        chatMembershipRepository.save(membership);
    }

    private Chat create(UUID initiatorId, UUID recipientId) {
        requireUser(initiatorId);
        requireUser(recipientId);

        Chat chat = chatRepository.save(Chat.builder().isGroup(false).build());
        chat.addMember(initiatorId, ChatStatus.ACCEPTED, true);
        chat.addMember(recipientId, ChatStatus.PENDING, false);
        chatMembershipRepository.saveAll(chat.getMemberships());
        return chat;
    }

    private void requireUser(UUID userId) {
        if (!userLookup.exists(userId)) {
            throw new UserNotFoundException("User not found: " + userId);
        }
    }
}

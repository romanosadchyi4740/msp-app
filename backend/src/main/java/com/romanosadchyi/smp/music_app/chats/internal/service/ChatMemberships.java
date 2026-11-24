package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.entity.Chat;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatStatus;
import lombok.experimental.UtilityClass;

import java.util.Optional;
import java.util.UUID;

/**
 * Finding and adding chat memberships, shared by the chat services.
 */
@UtilityClass
public class ChatMemberships {

    public Optional<ChatMembership> find(Chat chat, UUID userId) {
        return chat.getMemberships().stream()
                .filter(m -> m.getUserId().equals(userId))
                .findFirst();
    }

    /**
     * Whether the user may read the chat's messages and attachments: any member, except one who declined a group.
     * A pending invitee may read the chat they are invited to, and a member who rejected a direct chat may too,
     * since writing reopens it.
     */
    public boolean mayRead(Chat chat, UUID userId) {
        return find(chat, userId)
                .filter(membership -> !(Boolean.TRUE.equals(chat.getIsGroup())
                        && membership.getStatus() == ChatStatus.REJECTED))
                .isPresent();
    }

    /**
     * Adds a membership for {@code userId} to the chat and returns it; the caller saves it.
     */
    public ChatMembership add(Chat chat, UUID userId, ChatStatus status, boolean admin) {
        ChatMembership membership = ChatMembership.builder()
                .chat(chat)
                .userId(userId)
                .status(status)
                .isAdmin(admin)
                .build();
        chat.getMemberships().add(membership);
        return membership;
    }
}

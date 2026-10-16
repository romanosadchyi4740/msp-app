package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.entity.Chat;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.chats.internal.repository.ChatMembershipRepository;
import com.romanosadchyi.smp.music_app.chats.internal.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.chats.internal.repository.MessageRepository;
import com.romanosadchyi.smp.music_app.users.external.UserDataCleanup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * On account deletion: removes the user from every chat and deletes the messages they sent. A chat left without
 * members is deleted; the other members keep their chats and their own messages.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class ChatsUserDataCleanup implements UserDataCleanup {

    ChatRepository chatRepository;
    ChatMembershipRepository chatMembershipRepository;
    MessageRepository messageRepository;

    @Override
    public void deleteUserData(UUID userId) {
        for (ChatMembership membership : chatMembershipRepository.findAllByUserId(userId)) {
            Chat chat = membership.getChat();
            chat.getMemberships().remove(membership);
            if (chat.getMemberships().isEmpty()) {
                chatRepository.delete(chat);
            }
        }
        // After the chats above: a bulk delete bypasses the messages they cascade to
        messageRepository.deleteAllBySenderId(userId);
    }
}

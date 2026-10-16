package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.ChatNotFoundException;
import com.romanosadchyi.smp.music_app.model.dto.request.ChatMessageRequestDto;
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
 * The chat a new message goes to: the given chat, or the one-on-one chat with the given recipient. The sender
 * must be a member; a sender who had rejected the chat reopens it by writing.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MessageChatResolver {

    ChatRepository chatRepository;
    ChatMembershipRepository chatMembershipRepository;
    DirectChatResolver directChatResolver;
    UserLookup userLookup;

    /**
     * @throws UserNotFoundException    if the sender does not exist
     * @throws ChatNotFoundException    if {@code chatId} names no chat
     * @throws IllegalArgumentException if neither a chat nor a recipient is given, or the sender is not a member
     */
    public Chat resolve(ChatMessageRequestDto request, UUID senderId) {
        if (!userLookup.exists(senderId)) {
            throw new UserNotFoundException("User not found: " + senderId);
        }

        Chat chat = findChat(request, senderId);

        ChatMembership senderMembership = ChatMemberships.find(chat, senderId)
                .orElseThrow(() -> new IllegalArgumentException("User not in chat"));
        if (senderMembership.getStatus() == ChatStatus.REJECTED) {
            senderMembership.setStatus(ChatStatus.PENDING);
            senderMembership.setAdmin(true);
            chatMembershipRepository.save(senderMembership);
        }
        return chat;
    }

    private Chat findChat(ChatMessageRequestDto request, UUID senderId) {
        if (request.chatId() != null) {
            return chatRepository.findById(request.chatId())
                    .orElseThrow(() -> new ChatNotFoundException("Chat not found"));
        }
        if (request.recipientId() != null) {
            return directChatResolver.getOrCreate(senderId, request.recipientId());
        }
        throw new IllegalArgumentException("Either chatId or recipientId must be provided");
    }
}

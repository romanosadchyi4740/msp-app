package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMembershipDto;
import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatResponseDto;
import com.romanosadchyi.smp.music_app.chats.internal.entity.Chat;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatStatus;
import com.romanosadchyi.smp.music_app.chats.internal.exception.ChatNotFoundException;
import com.romanosadchyi.smp.music_app.chats.internal.mapping.ChatMappingService;
import com.romanosadchyi.smp.music_app.chats.internal.repository.ChatMembershipRepository;
import com.romanosadchyi.smp.music_app.chats.internal.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A member's own membership in a chat (accepting or rejecting an invitation) and the member list.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatMembershipService {

    ChatRepository chatRepository;
    ChatMembershipRepository chatMembershipRepository;
    UserLookup userLookup;
    ChatMappingService chatMappingService;

    @Transactional
    public ChatResponseDto acceptChat(UUID chatId, UUID userId) {
        Chat chat = findChat(chatId);
        ChatMembership membership = participantMembership(chat, userId);

        if (membership.isAdmin()) {
            throw new IllegalArgumentException("Initiator cannot accept their own chat");
        }

        membership.setStatus(ChatStatus.ACCEPTED);
        chatMembershipRepository.save(membership);
        return chatMappingService.map(chat, userId);
    }

    @Transactional
    public void rejectChat(UUID chatId, UUID userId) {
        ChatMembership membership = participantMembership(findChat(chatId), userId);
        membership.setStatus(ChatStatus.REJECTED);
        chatMembershipRepository.save(membership);
    }

    @Transactional(readOnly = true)
    public List<ChatMembershipDto> getChatMembers(UUID chatId, UUID requesterId) {
        Chat chat = findChat(chatId);
        if (ChatMemberships.find(chat, requesterId).isEmpty()) {
            throw new UserNotAuthorizedException("Requester is not a member of this chat");
        }

        Map<UUID, UserSummary> users = userLookup.findSummaries(
                chat.getMemberships().stream().map(ChatMembership::getUserId).toList());
        return chat.getMemberships().stream()
                .map(m -> ChatMembershipDto.builder()
                        .userId(m.getUserId())
                        .displayName(users.get(m.getUserId()).displayName())
                        .avatarUrl(users.get(m.getUserId()).avatarUrl())
                        .status(m.getStatus())
                        .isAdmin(m.isAdmin())
                        .build())
                .toList();
    }

    private Chat findChat(UUID chatId) {
        return chatRepository.findById(chatId)
                .orElseThrow(() -> new ChatNotFoundException("Chat not found"));
    }

    private static ChatMembership participantMembership(Chat chat, UUID userId) {
        return ChatMemberships.find(chat, userId)
                .orElseThrow(() -> new UserNotAuthorizedException("You are not a member of this chat"));
    }
}

package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.ChatNotFoundException;
import com.romanosadchyi.smp.music_app.model.dto.request.ClusterCreateRequestDto;
import com.romanosadchyi.smp.music_app.model.dto.response.ChatResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Chat;
import com.romanosadchyi.smp.music_app.model.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.model.enums.ChatStatus;
import com.romanosadchyi.smp.music_app.repository.ChatMembershipRepository;
import com.romanosadchyi.smp.music_app.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.service.mapping.ChatMappingService;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Group chats ("clusters"): creating one, and active members inviting more users. Invited users start as pending.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GroupChatService {

    ChatRepository chatRepository;
    ChatMembershipRepository chatMembershipRepository;
    UserLookup userLookup;
    ChatMappingService chatMappingService;

    @Transactional
    public ChatResponseDto createCluster(UUID creatorId, ClusterCreateRequestDto request) {
        requireUser(creatorId);

        Chat chat = chatRepository.save(Chat.builder()
                .isGroup(true)
                .name(request.groupName())
                .build());
        ChatMemberships.add(chat, creatorId, ChatStatus.ACCEPTED, true);

        for (UUID participantId : request.participantIds()) {
            if (participantId.equals(creatorId)) {
                continue;
            }
            requireUser(participantId);
            ChatMemberships.add(chat, participantId, ChatStatus.PENDING, false);
        }

        chatMembershipRepository.saveAll(chat.getMemberships());
        return chatMappingService.map(chat, creatorId);
    }

    @Transactional
    public void inviteMembers(UUID chatId, UUID requesterId, List<UUID> userIdsToInvite) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new ChatNotFoundException("Chat not found"));

        if (!chat.getIsGroup()) {
            throw new IllegalArgumentException("Cannot invite members to a 1-on-1 chat");
        }

        ChatMembership requesterMembership = ChatMemberships.find(chat, requesterId)
                .orElseThrow(() -> new UserNotAuthorizedException("Requester is not a member of this chat"));
        if (requesterMembership.getStatus() != ChatStatus.ACCEPTED) {
            throw new UserNotAuthorizedException("Requester is not an active member of this cluster");
        }

        Set<UUID> existingMemberIds = chat.getMemberships().stream()
                .map(ChatMembership::getUserId)
                .collect(Collectors.toSet());

        List<UUID> newMemberIds = userIdsToInvite.stream()
                .filter(id -> !existingMemberIds.contains(id))
                .toList();
        newMemberIds.forEach(this::requireUser);
        List<ChatMembership> newMemberships = newMemberIds.stream()
                .map(id -> ChatMemberships.add(chat, id, ChatStatus.PENDING, false))
                .toList();

        if (!newMemberships.isEmpty()) {
            chatMembershipRepository.saveAll(newMemberships);
        }
    }

    private void requireUser(UUID userId) {
        if (!userLookup.exists(userId)) {
            throw new UserNotFoundException("User not found: " + userId);
        }
    }
}

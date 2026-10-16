package com.romanosadchyi.smp.music_app.chats.internal.controller;

import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMembershipDto;
import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatResponseDto;
import com.romanosadchyi.smp.music_app.chats.internal.dto.ClusterCreateRequestDto;
import com.romanosadchyi.smp.music_app.chats.internal.service.ChatMembershipService;
import com.romanosadchyi.smp.music_app.chats.internal.service.ChatMessageService;
import com.romanosadchyi.smp.music_app.chats.internal.service.ChatService;
import com.romanosadchyi.smp.music_app.chats.internal.service.GroupChatService;
import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatController {

    ChatService chatService;
    GroupChatService groupChatService;
    ChatMembershipService chatMembershipService;
    ChatMessageService chatMessageService;

    @GetMapping
    public List<ChatResponseDto> getUserChats(@AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Getting chats for user: {}", user.id());
        return chatService.getUserChats(user.id());
    }

    @PostMapping("/with/{recipientId}")
    public ChatResponseDto getOrCreateChat(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID recipientId) {
        log.info("Getting or creating chat between {} and {}", user.id(), recipientId);
        return chatService.getOrCreateChatDto(user.id(), recipientId);
    }

    @PostMapping("/cluster")
    public ChatResponseDto createCluster(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody ClusterCreateRequestDto request) {
        log.info("Creating cluster {} with {} participants", request.groupName(), request.participantIds().size());
        return groupChatService.createCluster(user.id(), request);
    }

    @GetMapping("/{chatId}/messages")
    public Page<ChatMessageResponseDto> getMessageHistory(
            @PathVariable UUID chatId,
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("Getting message history for chat with ID: {}", chatId);
        return chatMessageService.getMessageHistory(chatId, pageable);
    }

    @PutMapping("/{chatId}/accept")
    public ChatResponseDto acceptChat(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID chatId) {
        log.info("User {} accepting chat {}", user.id(), chatId);
        return chatMembershipService.acceptChat(chatId, user.id());
    }

    @PutMapping("/{chatId}/reject")
    public void rejectChat(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID chatId) {
        log.info("User {} rejecting chat {}", user.id(), chatId);
        chatMembershipService.rejectChat(chatId, user.id());
    }

    @PostMapping("/{chatId}/membership/{userId}/decline")
    public void declineMembership(@AuthenticationPrincipal AuthenticatedUser authUser, @PathVariable UUID chatId, @PathVariable UUID userId) {
        if (!authUser.id().equals(userId)) {
            throw new IllegalArgumentException("Cannot decline membership for another user");
        }
        log.info("User {} declining membership for chat {}", userId, chatId);
        chatMembershipService.rejectChat(chatId, userId);
    }

    @GetMapping("/{chatId}/members")
    public List<ChatMembershipDto> getChatMembers(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID chatId) {
        log.info("Getting members for chat {}", chatId);
        return chatMembershipService.getChatMembers(chatId, user.id());
    }

    @PostMapping("/{chatId}/members")
    public void inviteMembers(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID chatId,
            @RequestBody List<UUID> userIds) {
        log.info("User {} inviting {} members to chat {}", user.id(), userIds.size(), chatId);
        groupChatService.inviteMembers(chatId, user.id(), userIds);
    }
}

package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.dto.response.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.ChatResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.service.ChatService;
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

    @GetMapping
    public List<ChatResponseDto> getUserChats(@AuthenticationPrincipal User user) {
        log.info("Getting chats for user: {}", user.getId());
        return chatService.getUserChats(user.getId());
    }

    @PostMapping("/with/{recipientId}")
    public ChatResponseDto getOrCreateChat(@AuthenticationPrincipal User user, @PathVariable UUID recipientId) {
        log.info("Getting or creating chat between {} and {}", user.getId(), recipientId);
        return chatService.getOrCreateChatDto(user.getId(), recipientId);
    }

    @PostMapping("/cluster")
    public ChatResponseDto createCluster(@AuthenticationPrincipal User user, @RequestBody com.romanosadchyi.smp.music_app.model.dto.request.ClusterCreateRequestDto request) {
        log.info("Creating cluster {} with {} participants", request.groupName(), request.participantIds().size());
        return chatService.createCluster(user.getId(), request);
    }

    @GetMapping("/{chatId}/messages")
    public Page<ChatMessageResponseDto> getMessageHistory(
            @PathVariable UUID chatId,
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("Getting message history for chat with ID: {}", chatId);
        return chatService.getMessageHistory(chatId, pageable);
    }

    @PutMapping("/{chatId}/accept")
    public ChatResponseDto acceptChat(@AuthenticationPrincipal User user, @PathVariable UUID chatId) {
        log.info("User {} accepting chat {}", user.getId(), chatId);
        return chatService.acceptChat(chatId, user.getId());
    }

    @PutMapping("/{chatId}/reject")
    public void rejectChat(@AuthenticationPrincipal User user, @PathVariable UUID chatId) {
        log.info("User {} rejecting chat {}", user.getId(), chatId);
        chatService.rejectChat(chatId, user.getId());
    }

    @PostMapping("/{chatId}/membership/{userId}/decline")
    public void declineMembership(@AuthenticationPrincipal User authUser, @PathVariable UUID chatId, @PathVariable UUID userId) {
        if (!authUser.getId().equals(userId)) {
            throw new IllegalArgumentException("Cannot decline membership for another user");
        }
        log.info("User {} declining membership for chat {}", userId, chatId);
        chatService.rejectChat(chatId, userId);
    }

    @GetMapping("/{chatId}/members")
    public List<com.romanosadchyi.smp.music_app.model.dto.response.ChatMembershipDto> getChatMembers(
            @AuthenticationPrincipal User user,
            @PathVariable UUID chatId) {
        log.info("Getting members for chat {}", chatId);
        return chatService.getChatMembers(chatId, user.getId());
    }

    @PostMapping("/{chatId}/members")
    public void inviteMembers(
            @AuthenticationPrincipal User user,
            @PathVariable UUID chatId,
            @RequestBody List<UUID> userIds) {
        log.info("User {} inviting {} members to chat {}", user.getId(), userIds.size(), chatId);
        chatService.inviteMembers(chatId, user.getId(), userIds);
    }
}
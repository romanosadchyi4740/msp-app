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

}

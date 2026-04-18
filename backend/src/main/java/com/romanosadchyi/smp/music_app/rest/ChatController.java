package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.dto.response.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/{chatId}/messages")
    public Page<ChatMessageResponseDto> getMessageHistory(
            @PathVariable UUID chatId,
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("Getting message history for chat with ID: {}", chatId);
        return chatService.getMessageHistory(chatId, pageable);
    }

}

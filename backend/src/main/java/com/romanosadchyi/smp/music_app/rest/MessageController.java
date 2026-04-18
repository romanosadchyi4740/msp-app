package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.dto.request.ChatMessageRequestDto;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MessageController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRepository chatRepository;

    @MessageMapping("/chat")
    public void processMessage(@Payload ChatMessageRequestDto chatMessageRequestDto, Authentication authentication) {
        User sender = (User) authentication.getPrincipal();
        log.info("Processing WebSocket message from {}: {}", sender.getEmail(), chatMessageRequestDto);
        chatService.processMessage(chatMessageRequestDto, sender.getId());
    }

}

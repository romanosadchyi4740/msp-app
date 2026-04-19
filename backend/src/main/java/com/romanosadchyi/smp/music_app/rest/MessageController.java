package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.dto.request.ChatMessageRequestDto;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.service.ChatService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.util.Objects;

@Slf4j
@Controller
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MessageController {

    ChatService chatService;

    @MessageMapping("/chat")
    public void processMessage(@Payload ChatMessageRequestDto chatMessageRequestDto, Authentication authentication) {
        User sender = (User) authentication.getPrincipal();
        log.info("Processing WebSocket message from {}: {}",
                Objects.requireNonNull(sender).getEmail(), chatMessageRequestDto);
        chatService.processMessage(chatMessageRequestDto, sender.getId());
    }

}

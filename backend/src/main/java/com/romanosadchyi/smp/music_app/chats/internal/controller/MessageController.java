package com.romanosadchyi.smp.music_app.chats.internal.controller;

import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageRequestDto;
import com.romanosadchyi.smp.music_app.chats.internal.service.ChatAttachmentService;
import com.romanosadchyi.smp.music_app.chats.internal.service.ChatMessageService;
import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MessageController {

    ChatMessageService chatMessageService;
    ChatAttachmentService chatAttachmentService;

    @MessageMapping("/chat")
    public void processMessage(@Payload ChatMessageRequestDto chatMessageRequestDto, Authentication authentication) {
        AuthenticatedUser sender = (AuthenticatedUser) authentication.getPrincipal();
        log.info("Processing WebSocket message from {}", Objects.requireNonNull(sender).id());
        chatMessageService.processMessage(chatMessageRequestDto, sender.id());
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@AuthenticationPrincipal AuthenticatedUser user,
                                             @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(chatAttachmentService.upload(file, user.id()));
    }

    /**
     * A download URL for a chat attachment, only for users who may read a chat with a message carrying it.
     */
    @GetMapping("/url")
    public ResponseEntity<String> getFileUrl(@AuthenticationPrincipal AuthenticatedUser user,
                                             @RequestParam("path") String path) {
        return ResponseEntity.ok(chatAttachmentService.presignedUrl(path, user.id()));
    }
}

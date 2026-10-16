package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.dto.request.ChatMessageRequestDto;
import com.romanosadchyi.smp.music_app.service.ChatService;
import com.romanosadchyi.smp.music_app.service.MinioService;
import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MessageController {

    ChatService chatService;
    MinioService minioService;

    @MessageMapping("/chat")
    public void processMessage(@Payload ChatMessageRequestDto chatMessageRequestDto, Authentication authentication) {
        AuthenticatedUser sender = (AuthenticatedUser) authentication.getPrincipal();
        log.info("Processing WebSocket message from {}", Objects.requireNonNull(sender).id());
        chatService.processMessage(chatMessageRequestDto, sender.id());
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        String objectName = "chat-attachments/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
        try {
            minioService.uploadFile(file, objectName);
            return ResponseEntity.ok(objectName);
        } catch (Exception e) {
            log.error("Failed to upload file", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/url")
    public ResponseEntity<String> getFileUrl(@RequestParam("path") String path) {
        try {
            return ResponseEntity.ok(minioService.generatePresignedUrl(path));
        } catch (Exception e) {
            log.error("Failed to generate presigned URL", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

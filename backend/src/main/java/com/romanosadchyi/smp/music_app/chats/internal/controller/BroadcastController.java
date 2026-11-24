package com.romanosadchyi.smp.music_app.chats.internal.controller;

import com.romanosadchyi.smp.music_app.chats.internal.dto.BroadcastSyncMessage;
import com.romanosadchyi.smp.music_app.chats.internal.mapping.BroadcastErrorMappingService;
import com.romanosadchyi.smp.music_app.chats.internal.service.BroadcastStateService;
import com.romanosadchyi.smp.music_app.shared.exception.ErrorResponseDto;
import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.util.UUID;

/**
 * Commands to a chat's listening broadcast (KAN-109). The server keeps the state and sends it to
 * {@code /topic/broadcast/{chatId}/{broadcastId}}; it no longer relays what members send, so only the host can
 * change what the listeners hear (KAN-110).
 */
@Controller
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BroadcastController {

    BroadcastStateService broadcastStateService;
    BroadcastErrorMappingService errorMappingService;

    @MessageMapping("/broadcast/{chatId}/{broadcastId}/sync")
    public void sync(@DestinationVariable UUID chatId, @DestinationVariable UUID broadcastId,
                     @Payload BroadcastSyncMessage message, Authentication authentication,
                     @Header(SimpMessageHeaderAccessor.SESSION_ID_HEADER) String sessionId) {
        UUID userId = ((AuthenticatedUser) authentication.getPrincipal()).id();
        if (message.command() == null) {
            throw new IllegalArgumentException("A command is required");
        }
        switch (message.command()) {
            case START -> broadcastStateService.start(chatId, broadcastId, userId, sessionId, message);
            case RESUME -> broadcastStateService.resume(chatId, broadcastId, userId, sessionId, message);
            case UPDATE -> broadcastStateService.update(chatId, broadcastId, userId, sessionId, message);
            case TERMINATE -> broadcastStateService.terminate(chatId, broadcastId, userId);
            case REQUEST_SYNC -> broadcastStateService.requestSync(chatId, broadcastId, userId);
        }
    }

    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/broadcast-errors", broadcast = false)
    public ErrorResponseDto handleError(Exception error) {
        return errorMappingService.map(error);
    }
}

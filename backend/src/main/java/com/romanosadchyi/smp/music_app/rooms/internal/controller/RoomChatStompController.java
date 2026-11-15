package com.romanosadchyi.smp.music_app.rooms.internal.controller;

import com.romanosadchyi.smp.music_app.rooms.internal.dto.RoomChatSendRequest;
import com.romanosadchyi.smp.music_app.rooms.internal.mapping.RoomChatErrorMappingService;
import com.romanosadchyi.smp.music_app.rooms.internal.service.RoomChatService;
import com.romanosadchyi.smp.music_app.shared.exception.ErrorResponseDto;
import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.util.UUID;

/**
 * The STOMP side of a room's chat (KAN-51). Only participants reach it: the STOMP guard of {@code rooms} checks every
 * frame to {@code /app/rooms/{roomId}/...}.
 * <ul>
 *     <li>{@code SEND /app/rooms/{roomId}/chat}: a {@link RoomChatSendRequest}; the stored message goes to
 *     {@code /topic/rooms/{roomId}/chat}.</li>
 *     <li>Errors go to the sender's {@code /user/queue/rooms/{roomId}/chat-errors} as an {@link ErrorResponseDto}, apart
 *     from the queue other features answer on, so the chat can tell its own failures apart.</li>
 * </ul>
 */
@Controller
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomChatStompController {

    RoomChatService roomChatService;
    RoomChatErrorMappingService errorMappingService;

    @MessageMapping("/rooms/{roomId}/chat")
    public void send(@DestinationVariable UUID roomId, @Payload RoomChatSendRequest request,
                     Authentication authentication) {
        roomChatService.send(((AuthenticatedUser) authentication.getPrincipal()).id(), roomId, request);
    }

    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/rooms/{roomId}/chat-errors", broadcast = false)
    public ErrorResponseDto handleError(Exception error) {
        return errorMappingService.map(error);
    }
}

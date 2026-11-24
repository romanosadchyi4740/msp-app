package com.romanosadchyi.smp.music_app.rtc.internal.controller;

import com.romanosadchyi.smp.music_app.rtc.internal.dto.SignalMessage;
import com.romanosadchyi.smp.music_app.rtc.internal.mapping.SignalErrorMappingService;
import com.romanosadchyi.smp.music_app.rtc.internal.service.SignalingService;
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
 * The STOMP endpoint of the WebRTC signaling (KAN-45). Only participants reach it: the STOMP guard of {@code rooms}
 * checks every frame to {@code /app/rooms/{roomId}/...}.
 * <ul>
 *     <li>{@code SEND /app/rooms/{roomId}/signal}: a {@link SignalMessage}; it goes to {@code /user/queue/signal} of
 *     the participant it names.</li>
 *     <li>Errors go to the sender's {@code /user/queue/errors} as an {@link ErrorResponseDto}.</li>
 * </ul>
 */
@Controller
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SignalController {

    SignalingService signalingService;
    SignalErrorMappingService signalErrorMappingService;

    @MessageMapping("/rooms/{roomId}/signal")
    public void signal(@DestinationVariable UUID roomId, @Payload SignalMessage signal,
                       Authentication authentication) {
        signalingService.relay(roomId, ((AuthenticatedUser) authentication.getPrincipal()).id(), signal);
    }

    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ErrorResponseDto handleError(Exception error) {
        return signalErrorMappingService.map(error);
    }
}

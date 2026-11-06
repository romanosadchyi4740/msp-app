package com.romanosadchyi.smp.music_app.sync.internal.controller;

import com.romanosadchyi.smp.music_app.shared.exception.ErrorResponseDto;
import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import com.romanosadchyi.smp.music_app.sync.internal.dto.RoomSyncSnapshot;
import com.romanosadchyi.smp.music_app.sync.internal.dto.SyncCommand;
import com.romanosadchyi.smp.music_app.sync.internal.mapping.SyncErrorMappingService;
import com.romanosadchyi.smp.music_app.sync.internal.service.RoomSyncService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.util.UUID;

/**
 * The STOMP endpoints of a room's sync channel (KAN-40). Only participants reach them: the STOMP guard of
 * {@code rooms} checks every frame to {@code /app/rooms/{roomId}/...}.
 * <ul>
 *     <li>{@code SEND /app/rooms/{roomId}/command}: a {@link SyncCommand}; the new state goes to
 *     {@code /topic/rooms/{roomId}/state}, a {@code REQUEST_SYNC} answer to
 *     {@code /user/queue/rooms/{roomId}/snapshot}.</li>
 *     <li>{@code SUBSCRIBE /app/rooms/{roomId}/snapshot}: answered at once with a {@link RoomSyncSnapshot}, to this
 *     subscription only; clients subscribe on every (re)connect, so joiners and reconnects always get a snapshot.</li>
 *     <li>Errors go to the sender's {@code /user/queue/errors} as an {@link ErrorResponseDto}.</li>
 * </ul>
 */
@Controller
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomSyncController {

    RoomSyncService roomSyncService;
    SyncErrorMappingService syncErrorMappingService;

    @MessageMapping("/rooms/{roomId}/command")
    public void command(@DestinationVariable UUID roomId, @Payload SyncCommand command,
                        Authentication authentication) {
        roomSyncService.handleCommand(roomId, userId(authentication), command);
    }

    @SubscribeMapping("/rooms/{roomId}/snapshot")
    public RoomSyncSnapshot snapshot(@DestinationVariable UUID roomId, Authentication authentication) {
        return roomSyncService.snapshot(roomId, userId(authentication));
    }

    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ErrorResponseDto handleError(Exception error) {
        return syncErrorMappingService.map(error);
    }

    private static UUID userId(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).id();
    }
}

package com.romanosadchyi.smp.music_app.sync.internal.controller;

import com.romanosadchyi.smp.music_app.shared.exception.ErrorResponseDto;
import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import com.romanosadchyi.smp.music_app.sync.internal.dto.RoomSyncSnapshot;
import com.romanosadchyi.smp.music_app.sync.internal.mapping.SyncErrorMappingService;
import com.romanosadchyi.smp.music_app.sync.internal.service.RoomSyncService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.util.UUID;

/**
 * A room's sync snapshot on subscription (KAN-40). Only participants reach it: the STOMP guard of {@code rooms}
 * checks every frame to {@code /app/rooms/{roomId}/...}.
 * <ul>
 *     <li>{@code SUBSCRIBE /app/rooms/{roomId}/snapshot}: answered at once with a {@link RoomSyncSnapshot}, to this
 *     subscription only; clients subscribe on every (re)connect, so joiners and reconnects always get a snapshot.</li>
 *     <li>A failure goes to the subscriber's {@code /user/queue/errors} as an {@link ErrorResponseDto}; refused
 *     commands have their own queue ({@link RoomSyncController}).</li>
 * </ul>
 */
@Controller
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomSnapshotController {

    RoomSyncService roomSyncService;
    SyncErrorMappingService syncErrorMappingService;

    @SubscribeMapping("/rooms/{roomId}/snapshot")
    public RoomSyncSnapshot snapshot(@DestinationVariable UUID roomId, Authentication authentication) {
        return roomSyncService.snapshot(roomId, ((AuthenticatedUser) authentication.getPrincipal()).id());
    }

    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ErrorResponseDto handleError(Exception error) {
        return syncErrorMappingService.map(error);
    }
}

package com.romanosadchyi.smp.music_app.sync.internal.controller;

import com.romanosadchyi.smp.music_app.shared.exception.ErrorResponseDto;
import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
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
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.util.UUID;

/**
 * The commands of a room's sync channel (KAN-40). Only participants reach it: the STOMP guard of {@code rooms}
 * checks every frame to {@code /app/rooms/{roomId}/...}.
 * <ul>
 *     <li>{@code SEND /app/rooms/{roomId}/command}: a {@link SyncCommand}; the new state goes to
 *     {@code /topic/rooms/{roomId}/state}, a {@code REQUEST_SYNC} answer to
 *     {@code /user/queue/rooms/{roomId}/snapshot}.</li>
 *     <li>A refused command goes to the sender's {@code /user/queue/rooms/{roomId}/command-errors} as an
 *     {@link ErrorResponseDto}, apart from the queue signaling and snapshots answer on, so the host's controls can
 *     tell their own failures apart however late they arrive (KAN-96). The sync engine's automatic
 *     {@code REQUEST_SYNC} is a command too, so its rare failure lands there as well.</li>
 * </ul>
 * The snapshot subscription is {@link RoomSnapshotController}'s, so its failures never look like a refused command.
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
        roomSyncService.handleCommand(roomId, ((AuthenticatedUser) authentication.getPrincipal()).id(), command);
    }

    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/rooms/{roomId}/command-errors", broadcast = false)
    public ErrorResponseDto handleError(Exception error) {
        return syncErrorMappingService.map(error);
    }
}

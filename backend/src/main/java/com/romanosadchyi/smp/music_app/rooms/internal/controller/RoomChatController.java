package com.romanosadchyi.smp.music_app.rooms.internal.controller;

import com.romanosadchyi.smp.music_app.rooms.internal.api.RoomChatApi;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomMessagePage;
import com.romanosadchyi.smp.music_app.rooms.internal.service.RoomChatService;
import com.romanosadchyi.smp.music_app.shared.security.CurrentUser;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Implements the interface generated from {@code openapi/rooms.yaml} (tag RoomChat), which holds the mappings; the
 * current user comes from {@link CurrentUser}. Messages are sent over STOMP ({@link RoomChatStompController}).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomChatController implements RoomChatApi {

    RoomChatService roomChatService;

    @Override
    public ResponseEntity<RoomMessagePage> getRoomMessages(UUID id, Integer pageNo, Integer pageSize) {
        UUID userId = CurrentUser.get().id();
        log.info("User {} reading the chat of room {}, page {}", userId, id, pageNo);
        return ResponseEntity.ok(roomChatService.history(userId, id, pageNo, pageSize));
    }
}

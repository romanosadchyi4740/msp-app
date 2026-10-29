package com.romanosadchyi.smp.music_app.rooms.internal.controller;

import com.romanosadchyi.smp.music_app.rooms.internal.api.RoomsApi;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomCreateRequest;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomListItem;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomPreview;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.service.RoomService;
import com.romanosadchyi.smp.music_app.shared.security.CurrentUser;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Implements the interface generated from {@code openapi/rooms.yaml}, which holds the mappings; the current user
 * comes from {@link CurrentUser}. Invite codes are not logged, since a code is enough to join.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomController implements RoomsApi {

    RoomService roomService;

    @Override
    public ResponseEntity<RoomResponse> createRoom(RoomCreateRequest request) {
        UUID userId = CurrentUser.get().id();
        log.info("Creating room for user: {}", userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.create(userId, request));
    }

    @Override
    public ResponseEntity<List<RoomListItem>> getMyRooms() {
        UUID userId = CurrentUser.get().id();
        log.info("Getting rooms of user: {}", userId);
        return ResponseEntity.ok(roomService.getMyRooms(userId));
    }

    @Override
    public ResponseEntity<RoomPreview> getRoomPreview(String code) {
        log.info("User {} previewing an invite", CurrentUser.get().id());
        return ResponseEntity.ok(roomService.getPreview(code));
    }

    @Override
    public ResponseEntity<RoomResponse> joinRoom(String code) {
        UUID userId = CurrentUser.get().id();
        log.info("User {} joining a room by invite", userId);
        return ResponseEntity.ok(roomService.join(userId, code));
    }

    @Override
    public ResponseEntity<RoomResponse> getRoom(UUID id) {
        UUID userId = CurrentUser.get().id();
        log.info("User {} getting room {}", userId, id);
        return ResponseEntity.ok(roomService.getRoom(userId, id));
    }

    @Override
    public ResponseEntity<RoomResponse> regenerateInvite(UUID id) {
        UUID userId = CurrentUser.get().id();
        log.info("User {} regenerating the invite of room {}", userId, id);
        return ResponseEntity.ok(roomService.regenerateInvite(userId, id));
    }

    @Override
    public ResponseEntity<RoomResponse> closeRoom(UUID id) {
        UUID userId = CurrentUser.get().id();
        log.info("User {} closing room {}", userId, id);
        return ResponseEntity.ok(roomService.close(userId, id));
    }
}

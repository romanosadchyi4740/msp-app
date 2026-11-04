package com.romanosadchyi.smp.music_app.rooms.internal.controller;

import com.romanosadchyi.smp.music_app.rooms.internal.api.LessonsApi;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.LessonResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.LessonTabRequest;
import com.romanosadchyi.smp.music_app.rooms.internal.service.LessonService;
import com.romanosadchyi.smp.music_app.shared.security.CurrentUser;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Implements the interface generated from {@code openapi/rooms.yaml} (tag Lessons), which holds the mappings; the
 * current user comes from {@link CurrentUser}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LessonController implements LessonsApi {

    LessonService lessonService;

    @Override
    public ResponseEntity<LessonResponse> startLesson(UUID id, LessonTabRequest request) {
        UUID userId = CurrentUser.get().id();
        log.info("User {} starting a lesson in room {}", userId, id);
        return ResponseEntity.status(HttpStatus.CREATED).body(lessonService.start(userId, id, request));
    }

    @Override
    public ResponseEntity<LessonResponse> getCurrentLesson(UUID id) {
        UUID userId = CurrentUser.get().id();
        log.info("User {} getting the lesson of room {}", userId, id);
        return ResponseEntity.ok(lessonService.getCurrent(userId, id));
    }

    @Override
    public ResponseEntity<LessonResponse> changeLessonTab(UUID id, LessonTabRequest request) {
        UUID userId = CurrentUser.get().id();
        log.info("User {} changing the tab of the lesson in room {}", userId, id);
        return ResponseEntity.ok(lessonService.changeTab(userId, id, request));
    }

    @Override
    public ResponseEntity<LessonResponse> endLesson(UUID id) {
        UUID userId = CurrentUser.get().id();
        log.info("User {} ending the lesson in room {}", userId, id);
        return ResponseEntity.ok(lessonService.end(userId, id));
    }
}

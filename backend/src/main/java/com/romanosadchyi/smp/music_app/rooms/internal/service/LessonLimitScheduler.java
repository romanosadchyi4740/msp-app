package com.romanosadchyi.smp.music_app.rooms.internal.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Every minute: warns rooms whose free lesson ends soon and ends the lessons that reached their limit. Each room is
 * checked in its own transaction, so a failure in one leaves the others alone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class LessonLimitScheduler {

    LessonService lessonService;

    @Scheduled(cron = "0 * * * * *")
    void checkLimits() {
        for (UUID roomId : lessonService.findRoomsDueForLimitCheck()) {
            try {
                lessonService.checkLimit(roomId);
            } catch (RuntimeException e) {
                log.error("Could not check the lesson limit of room {}", roomId, e);
            }
        }
    }
}

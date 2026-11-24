package com.romanosadchyi.smp.music_app.rooms.internal.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Every 10 seconds: hands rooms whose host has been away too long to another participant, so the role moves at most
 * about 70 seconds after the host went offline. Each room is handled in its own transaction, so a failure in one
 * leaves the others alone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class HostAwayScheduler {

    HostAwayService hostAwayService;

    @Scheduled(fixedDelay = 10_000)
    void handOverAwayHosts() {
        for (UUID roomId : hostAwayService.findRoomsWithHostAway()) {
            try {
                hostAwayService.handOver(roomId);
            } catch (RuntimeException e) {
                log.error("Could not hand over room {}", roomId, e);
            }
        }
    }
}

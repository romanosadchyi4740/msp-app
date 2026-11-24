package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonEndReason;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.LessonSessionRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The lesson limit, as {@link LessonLimitScheduler} applies it. It works from the stored start times, not from timers,
 * so after downtime the next run ends every lesson whose limit passed meanwhile.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LessonLimitService {

    LessonSessionRepository lessonRepository;
    RoomRepository roomRepository;
    LessonLifecycle lessonLifecycle;
    Clock clock;

    /**
     * The rooms whose lesson has reached its limit or is due a warning; {@link #checkLimit} then acts on each under
     * the room's lock.
     */
    @Transactional(readOnly = true)
    public List<UUID> findRoomsDueForLimitCheck() {
        Instant now = clock.instant();
        // Only lessons old enough for the warning of the shortest limit; the filter below applies each one's own
        Instant cutoff = now.minus(Duration.ofMinutes(LessonLifecycle.FREE_LIMIT_MINUTES))
                .plus(LessonSessions.WARNING_BEFORE_END);
        return lessonRepository.findActiveLimitedStartedBy(cutoff).stream()
                .filter(session -> LessonSessions.isExpired(session, now) || LessonSessions.isWarningDue(session, now))
                .map(session -> session.getRoom().getId())
                .toList();
    }

    /**
     * Ends the room's lesson if it has reached its limit, or warns the participants once when the limit is near.
     * Checks again under the room's lock, since the host may have ended the lesson meanwhile.
     */
    @Transactional
    public void checkLimit(UUID roomId) {
        if (roomRepository.findByIdForUpdate(roomId).isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        lessonRepository.findByRoomIdAndEndedAtIsNull(roomId).ifPresent(session -> {
            if (LessonSessions.isExpired(session, now)) {
                lessonLifecycle.end(session, LessonEndReason.LIMIT_REACHED);
                log.info("Lesson {} reached its limit of {} minutes", session.getId(), session.getLimitMinutes());
            } else if (LessonSessions.isWarningDue(session, now)) {
                lessonLifecycle.warnLimitNear(session);
                log.info("Warned room {} that lesson {} ends soon", roomId, session.getId());
            }
        });
    }
}

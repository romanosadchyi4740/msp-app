package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonSession;

import java.time.Duration;
import java.time.Instant;

/**
 * The length limit of a lesson (lab 7: {@code Session.isExpired}).
 */
public final class LessonSessions {

    /**
     * How long before the limit the participants are warned.
     */
    static final Duration WARNING_BEFORE_END = Duration.ofMinutes(5);

    private LessonSessions() {
    }

    /**
     * When the limit ends the lesson; null for a lesson without a limit.
     */
    public static Instant endsAt(LessonSession session) {
        if (session.getLimitMinutes() == null) {
            return null;
        }
        return session.getStartedAt().plus(Duration.ofMinutes(session.getLimitMinutes()));
    }

    static boolean isExpired(LessonSession session, Instant now) {
        Instant endsAt = endsAt(session);
        return endsAt != null && !now.isBefore(endsAt);
    }

    /**
     * The warning has not been sent and the limit is less than {@link #WARNING_BEFORE_END} away, but not reached.
     */
    static boolean isWarningDue(LessonSession session, Instant now) {
        Instant endsAt = endsAt(session);
        return endsAt != null && !session.isLimitWarningSent()
                && !now.isBefore(endsAt.minus(WARNING_BEFORE_END)) && now.isBefore(endsAt);
    }
}

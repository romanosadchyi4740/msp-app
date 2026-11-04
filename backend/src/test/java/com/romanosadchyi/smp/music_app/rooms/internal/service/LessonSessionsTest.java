package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonSession;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LessonSessionsTest {

    static final Instant STARTED = Instant.parse("2026-10-29T10:00:00Z");
    static final Instant ENDS = STARTED.plus(Duration.ofMinutes(40));

    @Test
    void endsAt_IsStartPlusTheLimit_OrNullWithoutOne() {
        assertThat(LessonSessions.endsAt(lesson(40))).isEqualTo(ENDS);
        assertThat(LessonSessions.endsAt(lesson(null))).isNull();
    }

    @Test
    void isExpired_FromTheLimitOn() {
        LessonSession lesson = lesson(40);

        assertThat(LessonSessions.isExpired(lesson, ENDS.minusMillis(1))).isFalse();
        assertThat(LessonSessions.isExpired(lesson, ENDS)).isTrue();
        assertThat(LessonSessions.isExpired(lesson, ENDS.plusSeconds(90))).isTrue();
    }

    @Test
    void isExpired_NeverWithoutALimit() {
        assertThat(LessonSessions.isExpired(lesson(null), STARTED.plus(Duration.ofDays(1)))).isFalse();
    }

    @Test
    void isWarningDue_InTheLastFiveMinutesOnly() {
        LessonSession lesson = lesson(40);

        assertThat(LessonSessions.isWarningDue(lesson, ENDS.minus(Duration.ofMinutes(5)).minusMillis(1))).isFalse();
        assertThat(LessonSessions.isWarningDue(lesson, ENDS.minus(Duration.ofMinutes(5)))).isTrue();
        assertThat(LessonSessions.isWarningDue(lesson, ENDS.minusMillis(1))).isTrue();
        assertThat(LessonSessions.isWarningDue(lesson, ENDS)).isFalse();
    }

    @Test
    void isWarningDue_NotOnceSentOrWithoutALimit() {
        LessonSession warned = lesson(40);
        warned.setLimitWarningSent(true);
        Instant fourMinutesLeft = ENDS.minus(Duration.ofMinutes(4));

        assertThat(LessonSessions.isWarningDue(warned, fourMinutesLeft)).isFalse();
        assertThat(LessonSessions.isWarningDue(lesson(null), fourMinutesLeft)).isFalse();
    }

    private static LessonSession lesson(Integer limitMinutes) {
        return LessonSession.builder().startedAt(STARTED).limitMinutes(limitMinutes).build();
    }
}

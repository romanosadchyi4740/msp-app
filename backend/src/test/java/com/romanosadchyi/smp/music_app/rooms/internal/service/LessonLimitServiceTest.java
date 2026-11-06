package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.billing.external.PremiumChecker;
import com.romanosadchyi.smp.music_app.rooms.external.LessonPlayback;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.LessonEvent;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.LessonEventType;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonEndReason;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonSession;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.LessonSessionRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Runs on a fixed clock with the real {@link LessonLifecycle}, so the 40-minute limit and the warning before it are
 * checked at exact instants.
 */
@ExtendWith(MockitoExtension.class)
class LessonLimitServiceTest {

    static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    @Mock
    LessonSessionRepository lessonRepository;

    @Mock
    RoomRepository roomRepository;

    @Mock
    PremiumChecker premiumChecker;

    @Mock
    LessonPlayback lessonPlayback;

    @Mock
    LessonNotifier lessonNotifier;

    LessonLimitService lessonLimitService;

    UUID hostId = UUID.randomUUID();
    UUID roomId = UUID.randomUUID();
    UUID tabId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    Room room;

    @BeforeEach
    void setUp() {
        lessonLimitService = serviceAt(NOW);
        room = Room.builder().id(roomId).hostId(hostId).status(RoomStatus.OPEN).build();
        lenient().when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        lenient().when(lessonRepository.save(any(LessonSession.class))).thenAnswer(invocation -> {
            LessonSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(sessionId);
            }
            return session;
        });
    }

    @Test
    void findRoomsDueForLimitCheck_OnlyExpiredOrWarningDue() {
        Room expiredRoom = Room.builder().id(UUID.randomUUID()).build();
        Room warnRoom = Room.builder().id(UUID.randomUUID()).build();
        Room warnedRoom = Room.builder().id(UUID.randomUUID()).build();
        Room earlyRoom = Room.builder().id(UUID.randomUUID()).build();
        LessonSession warned = lessonIn(warnedRoom, NOW.minus(Duration.ofMinutes(37)));
        warned.setLimitWarningSent(true);
        when(lessonRepository.findActiveLimitedStartedBy(NOW.minus(Duration.ofMinutes(35)))).thenReturn(List.of(
                lessonIn(expiredRoom, NOW.minus(Duration.ofMinutes(40))),
                lessonIn(warnRoom, NOW.minus(Duration.ofMinutes(35))),
                warned,
                lessonIn(earlyRoom, NOW.minus(Duration.ofMinutes(34)))));

        assertThat(lessonLimitService.findRoomsDueForLimitCheck()).containsExactly(expiredRoom.getId(), warnRoom.getId());
    }

    @Test
    void checkLimit_FiveMinutesBefore_WarnsOnce() {
        LessonSession lesson = activeLesson(40, NOW.minus(Duration.ofMinutes(35)));
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.of(lesson));

        lessonLimitService.checkLimit(roomId);
        lessonLimitService.checkLimit(roomId);

        assertThat(lesson.isLimitWarningSent()).isTrue();
        assertThat(lesson.getEndedAt()).isNull();
        LessonEvent event = publishedEvent();
        assertThat(event.type()).isEqualTo(LessonEventType.LIMIT_WARNING);
        assertThat(event.endsAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        verifyNoInteractions(lessonPlayback);
    }

    @Test
    void checkLimit_AtTheLimit_EndsTheLesson() {
        LessonSession lesson = activeLesson(40, NOW.minus(Duration.ofMinutes(40)));
        lesson.setLimitWarningSent(true);
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.of(lesson));

        lessonLimitService.checkLimit(roomId);

        assertThat(lesson.getEndedAt()).isEqualTo(NOW);
        assertThat(lesson.getEndReason()).isEqualTo(LessonEndReason.LIMIT_REACHED);
        verify(lessonPlayback).stop(roomId, sessionId);
        assertThat(publishedEvent().reason()).isEqualTo(LessonEndReason.LIMIT_REACHED);
    }

    @Test
    void checkLimit_BeforeTheWarningOrWithoutLimit_ChangesNothing() {
        LessonSession early = activeLesson(40, NOW.minus(Duration.ofMinutes(34)));
        LessonSession premium = activeLesson(null, NOW.minus(Duration.ofHours(3)));
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.of(early), Optional.of(premium));

        lessonLimitService.checkLimit(roomId);
        lessonLimitService.checkLimit(roomId);

        assertThat(early.isLimitWarningSent()).isFalse();
        assertThat(early.getEndedAt()).isNull();
        assertThat(premium.getEndedAt()).isNull();
        verify(lessonRepository, never()).save(any());
        verifyNoInteractions(lessonNotifier, lessonPlayback);
    }

    @Test
    void checkLimit_LessonEndedMeanwhileOrRoomDeleted_ChangesNothing() {
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.empty());
        UUID deletedRoomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(deletedRoomId)).thenReturn(Optional.empty());

        lessonLimitService.checkLimit(roomId);
        lessonLimitService.checkLimit(deletedRoomId);

        verify(lessonRepository, never()).save(any());
        verify(lessonRepository, never()).findByRoomIdAndEndedAtIsNull(deletedRoomId);
        verifyNoInteractions(lessonNotifier);
    }

    @Test
    void checkLimit_ALaterRun_EndsTheWarnedLesson() {
        LessonSession lesson = activeLesson(40, NOW.minus(Duration.ofMinutes(36)));
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.of(lesson));

        lessonLimitService.checkLimit(roomId);
        serviceAt(NOW.plus(Duration.ofMinutes(4))).checkLimit(roomId);

        assertThat(lesson.isLimitWarningSent()).isTrue();
        assertThat(lesson.getEndedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(4)));
        assertThat(lesson.getEndReason()).isEqualTo(LessonEndReason.LIMIT_REACHED);
    }

    private LessonEvent publishedEvent() {
        ArgumentCaptor<LessonEvent> event = ArgumentCaptor.forClass(LessonEvent.class);
        verify(lessonNotifier).publish(eq(roomId), event.capture());
        return event.getValue();
    }

    private LessonSession activeLesson(Integer limitMinutes, Instant startedAt) {
        return LessonSession.builder()
                .id(sessionId)
                .room(room)
                .tabId(tabId)
                .startedAt(startedAt)
                .limitMinutes(limitMinutes)
                .build();
    }

    private static LessonSession lessonIn(Room room, Instant startedAt) {
        return LessonSession.builder().id(UUID.randomUUID()).room(room).startedAt(startedAt).limitMinutes(40).build();
    }

    private LessonLimitService serviceAt(Instant now) {
        return new LessonLimitService(lessonRepository, roomRepository, lifecycleAt(now), Clock.fixed(now, ZoneOffset.UTC));
    }

    private LessonLifecycle lifecycleAt(Instant now) {
        return new LessonLifecycle(lessonRepository, premiumChecker, lessonPlayback, lessonNotifier,
                Clock.fixed(now, ZoneOffset.UTC));
    }
}

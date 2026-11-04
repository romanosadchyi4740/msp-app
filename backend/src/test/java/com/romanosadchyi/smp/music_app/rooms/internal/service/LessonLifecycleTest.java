package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.billing.external.PremiumChecker;
import com.romanosadchyi.smp.music_app.rooms.external.LessonPlayback;
import com.romanosadchyi.smp.music_app.rooms.external.LessonTab;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.LessonEvent;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.LessonEventType;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonEndReason;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonSession;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.LessonSessionRepository;
import com.romanosadchyi.smp.music_app.tabs.external.TabSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Runs on a fixed clock.
 */
@ExtendWith(MockitoExtension.class)
class LessonLifecycleTest {

    static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    @Mock
    LessonSessionRepository lessonRepository;

    @Mock
    PremiumChecker premiumChecker;

    @Mock
    LessonPlayback lessonPlayback;

    @Mock
    LessonNotifier lessonNotifier;

    LessonLifecycle lessonLifecycle;

    UUID hostId = UUID.randomUUID();
    UUID roomId = UUID.randomUUID();
    UUID tabId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    Room room;

    @BeforeEach
    void setUp() {
        lessonLifecycle = lifecycleAt(NOW);
        room = Room.builder().id(roomId).hostId(hostId).status(RoomStatus.OPEN).build();
        lenient().when(lessonRepository.save(any(LessonSession.class))).thenAnswer(invocation -> {
            LessonSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(sessionId);
            }
            return session;
        });
    }

    @Test
    void endActive_EndsWithTheReason_OrDoesNothingWithoutLesson() {
        LessonSession lesson = activeLesson(null, NOW.minus(Duration.ofHours(2)));
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.of(lesson), Optional.empty());

        lessonLifecycle.endActive(roomId, LessonEndReason.ROOM_CLOSED);
        lessonLifecycle.endActive(roomId, LessonEndReason.ROOM_CLOSED);

        assertThat(lesson.getEndReason()).isEqualTo(LessonEndReason.ROOM_CLOSED);
        assertThat(lesson.getEndedAt()).isEqualTo(NOW);
        verify(lessonPlayback).stop(sessionId);
    }

    @Test
    void start_FreePlan_SavesStartsPlaybackThenPublishes() {
        LessonSession session = lessonLifecycle.start(room, hostId, tab(null));

        assertThat(session.getStartedAt()).isEqualTo(NOW);
        assertThat(session.getLimitMinutes()).isEqualTo(40);
        InOrder order = inOrder(lessonRepository, lessonPlayback, lessonNotifier);
        order.verify(lessonRepository).save(session);
        order.verify(lessonPlayback).start(new LessonTab(sessionId, roomId, tabId, 120));
        order.verify(lessonNotifier).publish(eq(roomId), any());
        assertThat(publishedEvent().type()).isEqualTo(LessonEventType.LESSON_STARTED);
    }

    @Test
    void start_PlaybackFails_PublishesNothing() {
        doThrow(new IllegalStateException("sync down")).when(lessonPlayback).start(any());

        assertThatThrownBy(() -> lessonLifecycle.start(room, hostId, tab(90)))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(lessonNotifier);
    }

    @Test
    void start_Premium_HasNoLimit() {
        when(premiumChecker.hasPremium(hostId)).thenReturn(true);

        assertThat(lessonLifecycle.start(room, hostId, tab(90)).getLimitMinutes()).isNull();
    }

    @Test
    void changeTab_SetsTheTabAndRewindsPlayback() {
        LessonSession lesson = activeLesson(40, NOW);
        UUID newTabId = UUID.randomUUID();

        lessonLifecycle.changeTab(lesson, new TabSummary(newTabId, "Etude", 70, "3/4"));

        assertThat(lesson.getTabId()).isEqualTo(newTabId);
        verify(lessonPlayback).changeTab(new LessonTab(sessionId, roomId, newTabId, 70));
        assertThat(publishedEvent().type()).isEqualTo(LessonEventType.TAB_CHANGED);
    }

    @Test
    void warnLimitNear_MarksTheWarningAndPublishesTheEndTime() {
        LessonSession lesson = activeLesson(40, NOW.minus(Duration.ofMinutes(36)));

        lessonLifecycle.warnLimitNear(lesson);

        assertThat(lesson.isLimitWarningSent()).isTrue();
        verify(lessonRepository).save(lesson);
        LessonEvent event = publishedEvent();
        assertThat(event.type()).isEqualTo(LessonEventType.LIMIT_WARNING);
        assertThat(event.endsAt()).isEqualTo(NOW.plus(Duration.ofMinutes(4)));
        verifyNoInteractions(lessonPlayback);
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

    private TabSummary tab(Integer tempoBpm) {
        return new TabSummary(tabId, "Blues in A", tempoBpm, "4/4");
    }

    private LessonLifecycle lifecycleAt(Instant now) {
        return new LessonLifecycle(lessonRepository, premiumChecker, lessonPlayback, lessonNotifier,
                Clock.fixed(now, ZoneOffset.UTC));
    }
}

package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.billing.external.PremiumChecker;
import com.romanosadchyi.smp.music_app.rooms.external.LessonListener;
import com.romanosadchyi.smp.music_app.rooms.external.LessonTab;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.LessonResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.LessonTabRequest;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.LessonEvent;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.LessonEventType;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonEndReason;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonSession;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.LessonAlreadyActiveException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.LessonNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.LessonTabNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomClosedException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.mapping.LessonMappingService;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.LessonSessionRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.tabs.external.TabLookup;
import com.romanosadchyi.smp.music_app.tabs.external.TabSummary;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Runs on a fixed clock, so the 40-minute limit and the warning before it are checked at exact instants.
 */
@ExtendWith(MockitoExtension.class)
class LessonServiceTest {

    static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    @Mock
    LessonSessionRepository lessonRepository;

    @Mock
    RoomRepository roomRepository;

    @Mock
    RoomParticipantRepository participantRepository;

    @Mock
    TabLookup tabLookup;

    @Mock
    PremiumChecker premiumChecker;

    @Mock
    LessonListener lessonListener;

    @Mock
    LessonNotifier lessonNotifier;

    @Mock
    LessonMappingService lessonMappingService;

    LessonService lessonService;

    UUID hostId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UUID roomId = UUID.randomUUID();
    UUID tabId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    Room room;
    LessonResponse response = new LessonResponse();

    @BeforeEach
    void setUp() {
        lessonService = serviceAt(NOW);
        room = Room.builder().id(roomId).hostId(hostId).status(RoomStatus.OPEN).build();
        lenient().when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        lenient().when(participantRepository.findByRoomIdAndUserId(roomId, hostId))
                .thenReturn(Optional.of(participant(hostId, RoomRole.HOST)));
        lenient().when(participantRepository.findByRoomIdAndUserId(roomId, studentId))
                .thenReturn(Optional.of(participant(studentId, RoomRole.PARTICIPANT)));
        lenient().when(lessonMappingService.map(any())).thenReturn(response);
        lenient().when(lessonRepository.save(any(LessonSession.class))).thenAnswer(invocation -> {
            LessonSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(sessionId);
            }
            return session;
        });
    }

    @Test
    void start_FreePlan_HasA40MinuteLimitAndStartsPlaybackAtTheTabsTempo() {
        when(tabLookup.findReadable(tabId, hostId)).thenReturn(Optional.of(tab(90)));

        LessonResponse result = lessonService.start(hostId, roomId, new LessonTabRequest().tabId(tabId));

        ArgumentCaptor<LessonSession> saved = ArgumentCaptor.forClass(LessonSession.class);
        verify(lessonRepository).save(saved.capture());
        assertThat(saved.getValue().getRoom()).isSameAs(room);
        assertThat(saved.getValue().getTabId()).isEqualTo(tabId);
        assertThat(saved.getValue().getStartedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getLimitMinutes()).isEqualTo(40);
        verify(lessonListener).lessonStarted(new LessonTab(sessionId, roomId, tabId, 90));
        LessonEvent event = publishedEvent();
        assertThat(event.type()).isEqualTo(LessonEventType.LESSON_STARTED);
        assertThat(event.endsAt()).isEqualTo(NOW.plus(Duration.ofMinutes(40)));
        assertThat(result).isSameAs(response);
    }

    @Test
    void start_Premium_HasNoLimit() {
        when(tabLookup.findReadable(tabId, hostId)).thenReturn(Optional.of(tab(90)));
        when(premiumChecker.hasPremium(hostId)).thenReturn(true);

        lessonService.start(hostId, roomId, new LessonTabRequest().tabId(tabId));

        ArgumentCaptor<LessonSession> saved = ArgumentCaptor.forClass(LessonSession.class);
        verify(lessonRepository).save(saved.capture());
        assertThat(saved.getValue().getLimitMinutes()).isNull();
        assertThat(publishedEvent().endsAt()).isNull();
    }

    @Test
    void start_TabWithoutTempo_Uses120Bpm() {
        when(tabLookup.findReadable(tabId, hostId)).thenReturn(Optional.of(tab(null)));

        lessonService.start(hostId, roomId, new LessonTabRequest().tabId(tabId));

        verify(lessonListener).lessonStarted(new LessonTab(sessionId, roomId, tabId, 120));
    }

    @Test
    void start_WithoutTab_IsRejected() {
        assertThatThrownBy(() -> lessonService.start(hostId, roomId, new LessonTabRequest()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(roomRepository, lessonRepository, lessonListener);
    }

    @Test
    void start_ByParticipant_IsForbidden() {
        assertThatThrownBy(() -> lessonService.start(studentId, roomId, new LessonTabRequest().tabId(tabId)))
                .isInstanceOf(UserNotAuthorizedException.class);
        verifyNoInteractions(lessonRepository, lessonListener, lessonNotifier);
    }

    @Test
    void start_ByOutsiderOrInMissingRoom_IsNotFound() {
        UUID outsiderId = UUID.randomUUID();
        when(participantRepository.findByRoomIdAndUserId(roomId, outsiderId)).thenReturn(Optional.empty());
        UUID missingRoomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(missingRoomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lessonService.start(outsiderId, roomId, new LessonTabRequest().tabId(tabId)))
                .isInstanceOf(RoomNotFoundException.class);
        assertThatThrownBy(() -> lessonService.start(hostId, missingRoomId, new LessonTabRequest().tabId(tabId)))
                .isInstanceOf(RoomNotFoundException.class);
        verifyNoInteractions(lessonRepository);
    }

    @Test
    void start_ClosedRoom_IsRejected() {
        room.setStatus(RoomStatus.CLOSED);

        assertThatThrownBy(() -> lessonService.start(hostId, roomId, new LessonTabRequest().tabId(tabId)))
                .isInstanceOf(RoomClosedException.class);
        verify(lessonRepository, never()).save(any());
    }

    @Test
    void start_WhileALessonIsActive_IsRejected() {
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.of(activeLesson(40, NOW)));

        assertThatThrownBy(() -> lessonService.start(hostId, roomId, new LessonTabRequest().tabId(tabId)))
                .isInstanceOf(LessonAlreadyActiveException.class);
        verify(lessonRepository, never()).save(any());
    }

    @Test
    void start_TabTheHostMayNotRead_IsNotFound() {
        when(tabLookup.findReadable(tabId, hostId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lessonService.start(hostId, roomId, new LessonTabRequest().tabId(tabId)))
                .isInstanceOf(LessonTabNotFoundException.class);
        verify(lessonRepository, never()).save(any());
        verifyNoInteractions(lessonListener, lessonNotifier);
    }

    @Test
    void getCurrent_ForAParticipant_MapsTheActiveLesson() {
        LessonSession lesson = activeLesson(40, NOW);
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.of(lesson));
        when(lessonMappingService.map(lesson)).thenReturn(response);

        assertThat(lessonService.getCurrent(studentId, roomId)).isSameAs(response);
    }

    @Test
    void getCurrent_NoActiveLessonOrOutsider_IsNotFound() {
        UUID outsiderId = UUID.randomUUID();
        when(participantRepository.findByRoomIdAndUserId(roomId, outsiderId)).thenReturn(Optional.empty());
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lessonService.getCurrent(studentId, roomId))
                .isInstanceOf(LessonNotFoundException.class);
        assertThatThrownBy(() -> lessonService.getCurrent(outsiderId, roomId))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void changeTab_SetsTheTabAndRewindsPlayback() {
        LessonSession lesson = activeLesson(40, NOW.minus(Duration.ofMinutes(10)));
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.of(lesson));
        UUID newTabId = UUID.randomUUID();
        when(tabLookup.findReadable(newTabId, hostId))
                .thenReturn(Optional.of(new TabSummary(newTabId, "Etude", 70, "3/4")));

        lessonService.changeTab(hostId, roomId, new LessonTabRequest().tabId(newTabId));

        assertThat(lesson.getTabId()).isEqualTo(newTabId);
        verify(lessonRepository).save(lesson);
        verify(lessonListener).tabChanged(new LessonTab(sessionId, roomId, newTabId, 70));
        LessonEvent event = publishedEvent();
        assertThat(event.type()).isEqualTo(LessonEventType.TAB_CHANGED);
        assertThat(event.tabId()).isEqualTo(newTabId);
    }

    @Test
    void changeTab_ByParticipantOrWithoutLesson_IsRejected() {
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lessonService.changeTab(studentId, roomId, new LessonTabRequest().tabId(tabId)))
                .isInstanceOf(UserNotAuthorizedException.class);
        assertThatThrownBy(() -> lessonService.changeTab(hostId, roomId, new LessonTabRequest().tabId(tabId)))
                .isInstanceOf(LessonNotFoundException.class);
        verifyNoInteractions(lessonListener, lessonNotifier);
    }

    @Test
    void changeTab_TabTheHostMayNotRead_KeepsTheOldTab() {
        LessonSession lesson = activeLesson(40, NOW);
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.of(lesson));
        UUID privateTabId = UUID.randomUUID();
        when(tabLookup.findReadable(privateTabId, hostId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lessonService.changeTab(hostId, roomId, new LessonTabRequest().tabId(privateTabId)))
                .isInstanceOf(LessonTabNotFoundException.class);
        assertThat(lesson.getTabId()).isEqualTo(tabId);
        verifyNoInteractions(lessonListener);
    }

    @Test
    void end_ByHost_EndsTheLessonNow() {
        LessonSession lesson = activeLesson(40, NOW.minus(Duration.ofMinutes(15)));
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.of(lesson));

        lessonService.end(hostId, roomId);

        assertThat(lesson.getEndedAt()).isEqualTo(NOW);
        assertThat(lesson.getEndReason()).isEqualTo(LessonEndReason.HOST_ENDED);
        verify(lessonListener).lessonEnded(sessionId);
        LessonEvent event = publishedEvent();
        assertThat(event.type()).isEqualTo(LessonEventType.LESSON_ENDED);
        assertThat(event.reason()).isEqualTo(LessonEndReason.HOST_ENDED);
    }

    @Test
    void end_ByParticipantOrWithoutLesson_IsRejected() {
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lessonService.end(studentId, roomId))
                .isInstanceOf(UserNotAuthorizedException.class);
        assertThatThrownBy(() -> lessonService.end(hostId, roomId))
                .isInstanceOf(LessonNotFoundException.class);
        verifyNoInteractions(lessonListener, lessonNotifier);
    }

    @Test
    void endActiveLesson_EndsWithTheReason_OrDoesNothingWithoutLesson() {
        LessonSession lesson = activeLesson(null, NOW.minus(Duration.ofHours(2)));
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.of(lesson), Optional.empty());

        lessonService.endActiveLesson(roomId, LessonEndReason.ROOM_CLOSED);
        lessonService.endActiveLesson(roomId, LessonEndReason.ROOM_CLOSED);

        assertThat(lesson.getEndReason()).isEqualTo(LessonEndReason.ROOM_CLOSED);
        assertThat(lesson.getEndedAt()).isEqualTo(NOW);
        verify(lessonListener).lessonEnded(sessionId);
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

        assertThat(lessonService.findRoomsDueForLimitCheck()).containsExactly(expiredRoom.getId(), warnRoom.getId());
    }

    @Test
    void checkLimit_FiveMinutesBefore_WarnsOnce() {
        LessonSession lesson = activeLesson(40, NOW.minus(Duration.ofMinutes(35)));
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.of(lesson));

        lessonService.checkLimit(roomId);
        lessonService.checkLimit(roomId);

        assertThat(lesson.isLimitWarningSent()).isTrue();
        assertThat(lesson.getEndedAt()).isNull();
        LessonEvent event = publishedEvent();
        assertThat(event.type()).isEqualTo(LessonEventType.LIMIT_WARNING);
        assertThat(event.endsAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        verifyNoInteractions(lessonListener);
    }

    @Test
    void checkLimit_AtTheLimit_EndsTheLesson() {
        LessonSession lesson = activeLesson(40, NOW.minus(Duration.ofMinutes(40)));
        lesson.setLimitWarningSent(true);
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.of(lesson));

        lessonService.checkLimit(roomId);

        assertThat(lesson.getEndedAt()).isEqualTo(NOW);
        assertThat(lesson.getEndReason()).isEqualTo(LessonEndReason.LIMIT_REACHED);
        verify(lessonListener).lessonEnded(sessionId);
        assertThat(publishedEvent().reason()).isEqualTo(LessonEndReason.LIMIT_REACHED);
    }

    @Test
    void checkLimit_BeforeTheWarningOrWithoutLimit_ChangesNothing() {
        LessonSession early = activeLesson(40, NOW.minus(Duration.ofMinutes(34)));
        LessonSession premium = activeLesson(null, NOW.minus(Duration.ofHours(3)));
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.of(early), Optional.of(premium));

        lessonService.checkLimit(roomId);
        lessonService.checkLimit(roomId);

        assertThat(early.isLimitWarningSent()).isFalse();
        assertThat(early.getEndedAt()).isNull();
        assertThat(premium.getEndedAt()).isNull();
        verify(lessonRepository, never()).save(any());
        verifyNoInteractions(lessonNotifier, lessonListener);
    }

    @Test
    void checkLimit_LessonEndedMeanwhileOrRoomDeleted_ChangesNothing() {
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.empty());
        UUID deletedRoomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(deletedRoomId)).thenReturn(Optional.empty());

        lessonService.checkLimit(roomId);
        lessonService.checkLimit(deletedRoomId);

        verify(lessonRepository, never()).save(any());
        verify(lessonRepository, never()).findByRoomIdAndEndedAtIsNull(deletedRoomId);
        verifyNoInteractions(lessonNotifier);
    }

    @Test
    void checkLimit_ALaterRun_EndsTheWarnedLesson() {
        LessonSession lesson = activeLesson(40, NOW.minus(Duration.ofMinutes(36)));
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.of(lesson));

        lessonService.checkLimit(roomId);
        serviceAt(NOW.plus(Duration.ofMinutes(4))).checkLimit(roomId);

        assertThat(lesson.isLimitWarningSent()).isTrue();
        assertThat(lesson.getEndedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(4)));
        assertThat(lesson.getEndReason()).isEqualTo(LessonEndReason.LIMIT_REACHED);
    }

    private LessonService serviceAt(Instant now) {
        return new LessonService(lessonRepository, roomRepository, participantRepository, tabLookup, premiumChecker,
                List.of(lessonListener), lessonNotifier, lessonMappingService, Clock.fixed(now, ZoneOffset.UTC));
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

    private RoomParticipant participant(UUID userId, RoomRole role) {
        return RoomParticipant.builder().room(room).userId(userId).role(role).build();
    }

    private TabSummary tab(Integer tempoBpm) {
        return new TabSummary(tabId, "Blues in A", tempoBpm, "4/4");
    }
}

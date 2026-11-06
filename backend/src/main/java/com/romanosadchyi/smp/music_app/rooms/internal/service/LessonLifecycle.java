package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.billing.external.PremiumChecker;
import com.romanosadchyi.smp.music_app.rooms.external.LessonPlayback;
import com.romanosadchyi.smp.music_app.rooms.external.LessonTab;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.LessonEvent;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.LessonEventType;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonEndReason;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonSession;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.LessonSessionRepository;
import com.romanosadchyi.smp.music_app.tabs.external.TabSummary;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * The state changes of a lesson and what follows each of them: the lesson row, the playback in {@code sync} (same
 * transaction, so both commit or neither) and the event to the room (after the commit). Without Premium a lesson ends
 * after {@value #FREE_LIMIT_MINUTES} minutes. Callers hold the room's lock and have checked who may make the change,
 * so every method runs only inside the caller's transaction.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Transactional(propagation = Propagation.MANDATORY)
public class LessonLifecycle {

    static final int FREE_LIMIT_MINUTES = 40;

    LessonSessionRepository lessonRepository;
    PremiumChecker premiumChecker;
    LessonPlayback lessonPlayback;
    LessonNotifier lessonNotifier;
    Clock clock;

    /**
     * Starts a lesson in the room with the host's tab; the limit follows the host's plan.
     */
    public LessonSession start(Room room, UUID hostId, TabSummary tab) {
        LessonSession session = lessonRepository.save(LessonSession.builder()
                .room(room)
                .tabId(tab.id())
                .startedAt(clock.instant())
                .limitMinutes(premiumChecker.hasPremium(hostId) ? null : FREE_LIMIT_MINUTES)
                .build());
        lessonPlayback.start(lessonTab(session, tab));
        publish(session, LessonEventType.LESSON_STARTED);
        return session;
    }

    public void changeTab(LessonSession session, TabSummary tab) {
        session.setTabId(tab.id());
        lessonRepository.save(session);
        lessonPlayback.changeTab(lessonTab(session, tab));
        publish(session, LessonEventType.TAB_CHANGED);
    }

    public void end(LessonSession session, LessonEndReason reason) {
        session.setEndedAt(clock.instant());
        session.setEndReason(reason);
        lessonRepository.save(session);
        lessonPlayback.stop(session.getId());
        publish(session, LessonEventType.LESSON_ENDED);
    }

    /**
     * Ends the room's active lesson, if any.
     */
    public void endActive(UUID roomId, LessonEndReason reason) {
        lessonRepository.findByRoomIdAndEndedAtIsNull(roomId).ifPresent(session -> end(session, reason));
    }

    /**
     * Tells the room the limit is near, once per lesson.
     */
    public void warnLimitNear(LessonSession session) {
        session.setLimitWarningSent(true);
        lessonRepository.save(session);
        publish(session, LessonEventType.LIMIT_WARNING);
    }

    private void publish(LessonSession session, LessonEventType type) {
        lessonNotifier.publish(session.getRoom().getId(), new LessonEvent(type, session.getId(), session.getTabId(),
                LessonSessions.endsAt(session), session.getEndReason()));
    }

    private static LessonTab lessonTab(LessonSession session, TabSummary tab) {
        return new LessonTab(session.getId(), session.getRoom().getId(), tab.id(), LessonTabResolver.tempoOf(tab),
                tab.timeSignature());
    }
}

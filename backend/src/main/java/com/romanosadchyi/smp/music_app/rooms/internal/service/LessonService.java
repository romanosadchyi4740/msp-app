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
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The lesson lifecycle (lab 8, stages "Lesson" and "Wrap-up"): the host starts a lesson with a tab, changes the tab
 * and ends it. Without Premium a lesson ends after {@value #FREE_LIMIT_MINUTES} minutes, with a warning before. Every
 * change locks the room's row first, so start, end, close and the limit check of one room happen one after another;
 * per-lesson data of other modules follows through the {@link LessonListener}s.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LessonService {

    static final int FREE_LIMIT_MINUTES = 40;
    /**
     * The tempo of a lesson whose tab has none.
     */
    static final int DEFAULT_TEMPO_BPM = 120;

    LessonSessionRepository lessonRepository;
    RoomRepository roomRepository;
    RoomParticipantRepository participantRepository;
    TabLookup tabLookup;
    PremiumChecker premiumChecker;
    List<LessonListener> lessonListeners;
    LessonNotifier lessonNotifier;
    LessonMappingService lessonMappingService;
    Clock clock;

    /**
     * @throws IllegalArgumentException    if the tab id is missing
     * @throws RoomNotFoundException       if there is no such room or the user is not in it
     * @throws UserNotAuthorizedException  if the user is not the host
     * @throws RoomClosedException         if the room is closed
     * @throws LessonAlreadyActiveException if the room already has an active lesson
     * @throws LessonTabNotFoundException  if there is no such tab or the host may not read it
     */
    @Transactional
    public LessonResponse start(UUID userId, UUID roomId, LessonTabRequest request) {
        UUID tabId = requireTabId(request);
        Room room = lockRoomAsHost(userId, roomId, "Only the host can start a lesson");
        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new RoomClosedException("The room is closed");
        }
        if (lessonRepository.findByRoomIdAndEndedAtIsNull(roomId).isPresent()) {
            throw new LessonAlreadyActiveException("The room already has an active lesson");
        }
        TabSummary tab = readableTab(tabId, userId);

        LessonSession session = lessonRepository.save(LessonSession.builder()
                .room(room)
                .tabId(tab.id())
                .startedAt(clock.instant())
                .limitMinutes(premiumChecker.hasPremium(userId) ? null : FREE_LIMIT_MINUTES)
                .build());
        LessonTab lessonTab = new LessonTab(session.getId(), roomId, tab.id(), tempoOf(tab));
        lessonListeners.forEach(listener -> listener.lessonStarted(lessonTab));
        publish(session, LessonEventType.LESSON_STARTED);
        log.info("User {} started lesson {} in room {}", userId, session.getId(), roomId);
        return lessonMappingService.map(session);
    }

    /**
     * @throws RoomNotFoundException   if there is no such room or the user is not in it
     * @throws LessonNotFoundException if the room has no active lesson
     */
    @Transactional(readOnly = true)
    public LessonResponse getCurrent(UUID userId, UUID roomId) {
        if (participantRepository.findByRoomIdAndUserId(roomId, userId).isEmpty()) {
            throw new RoomNotFoundException("Room not found");
        }
        return lessonMappingService.map(activeLesson(roomId));
    }

    /**
     * Puts another tab on screen; playback starts over, stopped, at the new tab's tempo.
     *
     * @throws IllegalArgumentException   if the tab id is missing
     * @throws RoomNotFoundException      if there is no such room or the user is not in it
     * @throws UserNotAuthorizedException if the user is not the host
     * @throws LessonNotFoundException    if the room has no active lesson
     * @throws LessonTabNotFoundException if there is no such tab or the host may not read it
     */
    @Transactional
    public LessonResponse changeTab(UUID userId, UUID roomId, LessonTabRequest request) {
        UUID tabId = requireTabId(request);
        lockRoomAsHost(userId, roomId, "Only the host can change the tab");
        LessonSession session = activeLesson(roomId);
        TabSummary tab = readableTab(tabId, userId);

        session.setTabId(tab.id());
        lessonRepository.save(session);
        LessonTab lessonTab = new LessonTab(session.getId(), roomId, tab.id(), tempoOf(tab));
        lessonListeners.forEach(listener -> listener.tabChanged(lessonTab));
        publish(session, LessonEventType.TAB_CHANGED);
        log.info("User {} changed the tab of lesson {}", userId, session.getId());
        return lessonMappingService.map(session);
    }

    /**
     * @throws RoomNotFoundException      if there is no such room or the user is not in it
     * @throws UserNotAuthorizedException if the user is not the host
     * @throws LessonNotFoundException    if the room has no active lesson
     */
    @Transactional
    public LessonResponse end(UUID userId, UUID roomId) {
        lockRoomAsHost(userId, roomId, "Only the host can end the lesson");
        LessonSession session = activeLesson(roomId);
        endLesson(session, LessonEndReason.HOST_ENDED);
        log.info("User {} ended lesson {}", userId, session.getId());
        return lessonMappingService.map(session);
    }

    /**
     * Ends the room's active lesson, if any. The caller holds the room's lock and has checked who may do this, so this
     * runs only inside the caller's transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void endActiveLesson(UUID roomId, LessonEndReason reason) {
        lessonRepository.findByRoomIdAndEndedAtIsNull(roomId).ifPresent(session -> endLesson(session, reason));
    }

    /**
     * The rooms whose lesson has reached its limit or is due a warning; {@link #checkLimit} then acts on each under
     * the room's lock.
     */
    @Transactional(readOnly = true)
    public List<UUID> findRoomsDueForLimitCheck() {
        Instant now = clock.instant();
        // Only lessons old enough for the warning of the shortest limit; the filter below applies each one's own
        Instant cutoff = now.minus(Duration.ofMinutes(FREE_LIMIT_MINUTES)).plus(LessonSessions.WARNING_BEFORE_END);
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
                endLesson(session, LessonEndReason.LIMIT_REACHED);
                log.info("Lesson {} reached its limit of {} minutes", session.getId(), session.getLimitMinutes());
            } else if (LessonSessions.isWarningDue(session, now)) {
                session.setLimitWarningSent(true);
                lessonRepository.save(session);
                publish(session, LessonEventType.LIMIT_WARNING);
                log.info("Warned room {} that lesson {} ends soon", roomId, session.getId());
            }
        });
    }

    private void endLesson(LessonSession session, LessonEndReason reason) {
        session.setEndedAt(clock.instant());
        session.setEndReason(reason);
        lessonRepository.save(session);
        lessonListeners.forEach(listener -> listener.lessonEnded(session.getId()));
        publish(session, LessonEventType.LESSON_ENDED);
    }

    private void publish(LessonSession session, LessonEventType type) {
        lessonNotifier.publish(session.getRoom().getId(), new LessonEvent(type, session.getId(), session.getTabId(),
                LessonSessions.endsAt(session), session.getEndReason()));
    }

    /**
     * Locks the room before checking the user's role, so the check sees the room as it is under the lock.
     */
    private Room lockRoomAsHost(UUID userId, UUID roomId, String notHostMessage) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found"));
        RoomRole role = participantRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found"))
                .getRole();
        if (role != RoomRole.HOST) {
            throw new UserNotAuthorizedException(notHostMessage);
        }
        return room;
    }

    private LessonSession activeLesson(UUID roomId) {
        return lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)
                .orElseThrow(() -> new LessonNotFoundException("The room has no active lesson"));
    }

    private TabSummary readableTab(UUID tabId, UUID userId) {
        return tabLookup.findReadable(tabId, userId)
                .orElseThrow(() -> new LessonTabNotFoundException("Tab not found"));
    }

    private static UUID requireTabId(LessonTabRequest request) {
        if (request == null || request.getTabId() == null) {
            throw new IllegalArgumentException("A tab is required");
        }
        return request.getTabId();
    }

    private static int tempoOf(TabSummary tab) {
        return tab.tempoBpm() != null ? tab.tempoBpm() : DEFAULT_TEMPO_BPM;
    }
}

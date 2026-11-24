package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.LessonResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.LessonTabRequest;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonEndReason;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonSession;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.LessonAlreadyActiveException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.LessonNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.LessonTabNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomClosedException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.mapping.LessonMappingService;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.LessonSessionRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.tabs.external.TabSummary;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The lesson use cases of the REST API (lab 8, stages "Lesson" and "Wrap-up"): the host starts a lesson with a tab,
 * changes the tab and ends it; any participant reads the active lesson. Every change locks the room's row first, so
 * start, end, close and the limit check of one room happen one after another; {@link LessonLifecycle} makes the
 * change itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LessonService {

    RoomAccess roomAccess;
    LessonTabResolver lessonTabResolver;
    LessonLifecycle lessonLifecycle;
    LessonSessionRepository lessonRepository;
    LessonMappingService lessonMappingService;

    /**
     * @throws IllegalArgumentException     if the tab id is missing
     * @throws RoomNotFoundException        if there is no such room or the user is not in it
     * @throws UserNotAuthorizedException   if the user is not the host
     * @throws RoomClosedException          if the room is closed
     * @throws LessonAlreadyActiveException if the room already has an active lesson
     * @throws LessonTabNotFoundException   if there is no such tab or the host may not read it
     */
    @Transactional
    public LessonResponse start(UUID userId, UUID roomId, LessonTabRequest request) {
        UUID tabId = lessonTabResolver.requireTabId(request);
        Room room = roomAccess.lockAsHost(userId, roomId, "Only the host can start a lesson");
        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new RoomClosedException("The room is closed");
        }
        if (lessonRepository.findByRoomIdAndEndedAtIsNull(roomId).isPresent()) {
            throw new LessonAlreadyActiveException("The room already has an active lesson");
        }
        TabSummary tab = lessonTabResolver.readableTab(tabId, userId);

        LessonSession session = lessonLifecycle.start(room, userId, tab);
        log.info("User {} started lesson {} in room {}", userId, session.getId(), roomId);
        return lessonMappingService.map(session);
    }

    /**
     * @throws RoomNotFoundException   if there is no such room or the user is not in it
     * @throws LessonNotFoundException if the room has no active lesson
     */
    @Transactional(readOnly = true)
    public LessonResponse getCurrent(UUID userId, UUID roomId) {
        roomAccess.requireParticipant(userId, roomId);
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
        UUID tabId = lessonTabResolver.requireTabId(request);
        roomAccess.lockAsHost(userId, roomId, "Only the host can change the tab");
        LessonSession session = activeLesson(roomId);
        TabSummary tab = lessonTabResolver.readableTab(tabId, userId);

        lessonLifecycle.changeTab(session, tab);
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
        roomAccess.lockAsHost(userId, roomId, "Only the host can end the lesson");
        LessonSession session = activeLesson(roomId);
        lessonLifecycle.end(session, LessonEndReason.HOST_ENDED);
        log.info("User {} ended lesson {}", userId, session.getId());
        return lessonMappingService.map(session);
    }

    private LessonSession activeLesson(UUID roomId) {
        return lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)
                .orElseThrow(() -> new LessonNotFoundException("The room has no active lesson"));
    }
}

package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.external.RoomLessonAccess;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonSession;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.LessonNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.LessonSessionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class RoomLessonAccessService implements RoomLessonAccess {

    RoomAccess roomAccess;
    LessonSessionRepository lessonRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID lockActiveLessonAsHost(UUID roomId, UUID userId) {
        roomAccess.lockAsHost(userId, roomId, "Only the host can control playback");
        return lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)
                .map(LessonSession::getId)
                .orElseThrow(() -> new LessonNotFoundException("The room has no active lesson"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findActiveLesson(UUID roomId, UUID userId) {
        roomAccess.requireParticipant(userId, roomId);
        return lessonRepository.findByRoomIdAndEndedAtIsNull(roomId).map(LessonSession::getId);
    }
}

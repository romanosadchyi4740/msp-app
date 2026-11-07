package com.romanosadchyi.smp.music_app.rooms.internal.repository;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LessonSessionRepository extends JpaRepository<LessonSession, UUID> {
    /**
     * The room's active lesson, if any.
     */
    Optional<LessonSession> findByRoomIdAndEndedAtIsNull(UUID roomId);

    /**
     * Active lessons that have a length limit and started at or before {@code cutoff}; uses the partial index
     * {@code idx_lesson_sessions_active_limited}.
     */
    @Query("""
            SELECT s FROM LessonSession s
            WHERE s.endedAt IS NULL AND s.limitMinutes IS NOT NULL AND s.startedAt <= :cutoff
            """)
    List<LessonSession> findActiveLimitedStartedBy(@Param("cutoff") Instant cutoff);

    /**
     * Whether the tab is the tab of an active lesson in a room the user takes part in; uses the indexes on
     * {@code lesson_sessions.tab_id} and {@code room_participants(room_id, user_id)}.
     */
    @Query("""
            SELECT COUNT(s) > 0 FROM LessonSession s, RoomParticipant p
            WHERE s.tabId = :tabId AND s.endedAt IS NULL AND p.room = s.room AND p.userId = :userId
            """)
    boolean isActiveLessonTabOfParticipant(@Param("tabId") UUID tabId, @Param("userId") UUID userId);
}

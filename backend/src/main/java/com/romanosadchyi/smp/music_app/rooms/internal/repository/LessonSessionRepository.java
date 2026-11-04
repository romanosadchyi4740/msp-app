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
}

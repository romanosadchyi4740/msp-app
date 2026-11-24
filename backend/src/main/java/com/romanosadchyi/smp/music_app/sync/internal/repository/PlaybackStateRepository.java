package com.romanosadchyi.smp.music_app.sync.internal.repository;

import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlaybackStateRepository extends JpaRepository<PlaybackState, UUID> {

    /**
     * The state with its row locked until the transaction ends, so commands to one lesson apply one after another.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PlaybackState s WHERE s.sessionId = :sessionId")
    Optional<PlaybackState> findByIdForUpdate(@Param("sessionId") UUID sessionId);
}

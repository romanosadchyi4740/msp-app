package com.romanosadchyi.smp.music_app.telemetry.internal.repository;

import com.romanosadchyi.smp.music_app.telemetry.internal.entity.StatusBroadcast;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StatusBroadcastRepository extends JpaRepository<StatusBroadcast, UUID> {

    @Query("SELECT b FROM StatusBroadcast b " +
           "LEFT JOIN FETCH b.poll p " +
           "LEFT JOIN FETCH p.options " +
           "WHERE b.authorId IN :authorIds " +
           "ORDER BY b.timestamp DESC")
    Page<StatusBroadcast> findAllByAuthorIds(@Param("authorIds") Collection<UUID> authorIds, Pageable pageable);

    List<StatusBroadcast> findAllByAuthorId(UUID authorId);

    @Modifying
    @Query("UPDATE StatusBroadcast b SET b.attachedTrackId = null WHERE b.attachedTrackId = :trackId")
    void detachTrack(@Param("trackId") UUID trackId);
}

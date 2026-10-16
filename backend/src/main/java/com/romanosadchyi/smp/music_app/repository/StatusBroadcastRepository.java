package com.romanosadchyi.smp.music_app.repository;

import com.romanosadchyi.smp.music_app.model.entity.StatusBroadcast;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.UUID;

public interface StatusBroadcastRepository extends JpaRepository<StatusBroadcast, UUID> {

    @Query("SELECT b FROM StatusBroadcast b " +
           "LEFT JOIN FETCH b.attachedTrack " +
           "LEFT JOIN FETCH b.poll p " +
           "LEFT JOIN FETCH p.options " +
           "WHERE b.authorId IN :authorIds " +
           "ORDER BY b.timestamp DESC")
    Page<StatusBroadcast> findAllByAuthorIds(@Param("authorIds") Collection<UUID> authorIds, Pageable pageable);
}

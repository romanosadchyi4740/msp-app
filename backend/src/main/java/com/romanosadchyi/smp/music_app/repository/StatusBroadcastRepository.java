package com.romanosadchyi.smp.music_app.repository;

import com.romanosadchyi.smp.music_app.model.entity.StatusBroadcast;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface StatusBroadcastRepository extends JpaRepository<StatusBroadcast, UUID> {

    @Query("SELECT b FROM StatusBroadcast b JOIN FETCH b.author " +
           "LEFT JOIN FETCH b.attachedTrack " +
           "LEFT JOIN FETCH b.poll p " +
           "LEFT JOIN FETCH p.options " +
           "WHERE b.author IN (SELECT f FROM User u JOIN u.following f WHERE u.id = :userId) " +
           "ORDER BY b.timestamp DESC")
    Page<StatusBroadcast> findFeedForUser(@Param("userId") UUID userId, Pageable pageable);
}

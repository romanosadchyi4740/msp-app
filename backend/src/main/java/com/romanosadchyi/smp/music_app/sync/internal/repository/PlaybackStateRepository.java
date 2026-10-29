package com.romanosadchyi.smp.music_app.sync.internal.repository;

import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PlaybackStateRepository extends JpaRepository<PlaybackState, UUID> {
}

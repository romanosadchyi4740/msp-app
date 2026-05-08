package com.romanosadchyi.smp.music_app.repository;

import com.romanosadchyi.smp.music_app.model.entity.Track;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TrackRepository extends JpaRepository<Track, UUID> {
    Page<Track> findAllByTitleContainingIgnoreCase(String title, Pageable pageable);
    Page<Track> findAllByAuthorIdAndTitleContainingIgnoreCase(UUID authorId, String title, Pageable pageable);
    
    Page<Track> findByGenresId(UUID genreId, Pageable pageable);
    List<Track> findByGenresIdAndIdNotIn(UUID genreId, Collection<UUID> excludedIds, Pageable pageable);

    Boolean existsByFileHash(String fileHash);
}

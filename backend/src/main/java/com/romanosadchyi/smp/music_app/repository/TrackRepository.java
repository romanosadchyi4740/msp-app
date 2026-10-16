package com.romanosadchyi.smp.music_app.repository;

import com.romanosadchyi.smp.music_app.model.entity.Track;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TrackRepository extends JpaRepository<Track, UUID> {
    List<Track> findAllByAuthorId(UUID authorId);
    Page<Track> findAllByTitleContainingIgnoreCase(String title, Pageable pageable);
    Page<Track> findAllByAuthorIdAndTitleContainingIgnoreCase(UUID authorId, String title, Pageable pageable);
    
    Page<Track> findByGenresId(UUID genreId, Pageable pageable);
    List<Track> findByGenresIdAndIdNotIn(UUID genreId, Collection<UUID> excludedIds, Pageable pageable);

    Boolean existsByFileHash(String fileHash);

    @Query("SELECT t FROM Track t JOIN t.likedByUserIds u WHERE u = :userId")
    List<Track> findAllLikedBy(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM user_liked_tracks WHERE user_id = :userId", nativeQuery = true)
    void deleteLikesByUser(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM playlist_tracks WHERE track_id = :trackId", nativeQuery = true)
    void deleteFromPlaylistTracks(@Param("trackId") UUID trackId);

    @Modifying
    @Query(value = "DELETE FROM track_genres WHERE track_id = :trackId", nativeQuery = true)
    void deleteFromTrackGenres(@Param("trackId") UUID trackId);
}

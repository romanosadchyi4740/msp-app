package com.romanosadchyi.smp.music_app.playlists.internal.repository;

import com.romanosadchyi.smp.music_app.playlists.internal.entity.Playlist;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {

    /**
     * Fetches each playlist's origin and track ids with the playlists, since {@code PlaylistMappingService.mapAll}
     * reads both; otherwise a list would load them once per playlist.
     */
    @EntityGraph(attributePaths = {"forkedFrom", "trackIds"})
    List<Playlist> findAllByOwnerId(UUID ownerId);

    /**
     * Like {@link #findAllByOwnerId}, public playlists only.
     */
    @EntityGraph(attributePaths = {"forkedFrom", "trackIds"})
    List<Playlist> findAllByOwnerIdAndIsPublicTrue(UUID ownerId);

    @Query("SELECT DISTINCT p FROM Playlist p JOIN p.trackIds t WHERE t = :trackId")
    List<Playlist> findAllContainingTrack(@Param("trackId") UUID trackId);

    @Modifying
    @Query("UPDATE Playlist p SET p.forkedFrom = null WHERE p.forkedFrom = :playlist")
    void detachParentPlaylist(@Param("playlist") Playlist playlist);
}

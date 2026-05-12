package com.romanosadchyi.smp.music_app.repository;

import com.romanosadchyi.smp.music_app.model.entity.Playlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {

    List<Playlist> findAllByOwnerId(UUID ownerId);
    List<Playlist> findAllByOwnerIdAndIsPublicTrue(UUID ownerId);

    @Modifying
    @Query("UPDATE Playlist p SET p.forkedFrom = null WHERE p.forkedFrom = :playlist")
    void detachParentPlaylist(@Param("playlist") Playlist playlist);

}

package com.romanosadchyi.smp.music_app.playlists.internal.repository;

import com.romanosadchyi.smp.music_app.playlists.internal.entity.IntegrationRequest;
import com.romanosadchyi.smp.music_app.playlists.internal.entity.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IntegrationRequestRepository extends JpaRepository<IntegrationRequest, UUID> {
    List<IntegrationRequest> findAllByTargetPlaylistIdAndStatusOrderByCreatedAtDesc(UUID targetPlaylistId, RequestStatus status);

    List<IntegrationRequest> findAllByRequesterId(UUID requesterId);

    @Query("SELECT DISTINCT r FROM IntegrationRequest r JOIN r.proposedTrackIds t WHERE t = :trackId")
    List<IntegrationRequest> findAllProposingTrack(@Param("trackId") UUID trackId);
}

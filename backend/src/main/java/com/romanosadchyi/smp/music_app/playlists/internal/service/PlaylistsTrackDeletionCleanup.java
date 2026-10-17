package com.romanosadchyi.smp.music_app.playlists.internal.service;

import com.romanosadchyi.smp.music_app.music.external.TrackDeletionCleanup;
import com.romanosadchyi.smp.music_app.playlists.internal.entity.IntegrationRequest;
import com.romanosadchyi.smp.music_app.playlists.internal.entity.RequestStatus;
import com.romanosadchyi.smp.music_app.playlists.internal.repository.IntegrationRequestRepository;
import com.romanosadchyi.smp.music_app.playlists.internal.repository.PlaylistRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Removes a deleted track from playlists and from the integration requests that propose it. A pending request left
 * without proposed tracks is rejected, so it leaves the owner's pending list but stays in the history.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class PlaylistsTrackDeletionCleanup implements TrackDeletionCleanup {

    PlaylistRepository playlistRepository;
    IntegrationRequestRepository integrationRequestRepository;

    @Override
    public void deleteTrackReferences(UUID trackId) {
        // Through the entity, so Hibernate renumbers track_order instead of leaving a gap
        playlistRepository.findAllContainingTrack(trackId)
                .forEach(playlist -> playlist.getTrackIds().removeIf(trackId::equals));
        integrationRequestRepository.findAllProposingTrack(trackId)
                .forEach(request -> removeProposedTrack(request, trackId));
    }

    private static void removeProposedTrack(IntegrationRequest request, UUID trackId) {
        request.getProposedTrackIds().remove(trackId);
        if (request.getProposedTrackIds().isEmpty() && request.getStatus() == RequestStatus.PENDING) {
            request.setStatus(RequestStatus.REJECTED);
        }
    }
}

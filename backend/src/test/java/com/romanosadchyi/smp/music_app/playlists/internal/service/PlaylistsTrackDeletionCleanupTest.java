package com.romanosadchyi.smp.music_app.playlists.internal.service;

import com.romanosadchyi.smp.music_app.playlists.internal.entity.IntegrationRequest;
import com.romanosadchyi.smp.music_app.playlists.internal.entity.Playlist;
import com.romanosadchyi.smp.music_app.playlists.internal.repository.IntegrationRequestRepository;
import com.romanosadchyi.smp.music_app.playlists.internal.repository.PlaylistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaylistsTrackDeletionCleanupTest {

    @Mock
    PlaylistRepository playlistRepository;

    @Mock
    IntegrationRequestRepository integrationRequestRepository;

    @InjectMocks
    PlaylistsTrackDeletionCleanup cleanup;

    @Test
    void deleteTrackReferences_RemovesEveryOccurrenceFromPlaylists() {
        UUID deleted = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        Playlist playlist = Playlist.builder().trackIds(new ArrayList<>(List.of(kept, deleted, kept, deleted))).build();
        when(playlistRepository.findAllContainingTrack(deleted)).thenReturn(List.of(playlist));

        cleanup.deleteTrackReferences(deleted);

        assertThat(playlist.getTrackIds()).containsExactly(kept, kept);
    }

    @Test
    void deleteTrackReferences_RemovesTheTrackFromProposedTracks() {
        UUID deleted = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        IntegrationRequest request = IntegrationRequest.builder()
                .proposedTrackIds(new LinkedHashSet<>(List.of(deleted, kept)))
                .build();
        when(integrationRequestRepository.findAllProposingTrack(deleted)).thenReturn(List.of(request));

        cleanup.deleteTrackReferences(deleted);

        assertThat(request.getProposedTrackIds()).containsExactly(kept);
    }
}

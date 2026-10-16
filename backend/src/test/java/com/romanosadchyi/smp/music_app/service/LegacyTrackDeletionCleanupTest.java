package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.entity.Playlist;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.repository.MessageRepository;
import com.romanosadchyi.smp.music_app.repository.PlaylistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyTrackDeletionCleanupTest {

    @Mock
    PlaylistRepository playlistRepository;

    @Mock
    MessageRepository messageRepository;

    @InjectMocks
    LegacyTrackDeletionCleanup cleanup;

    @Test
    void deleteTrackReferences_RemovesTrackFromPlaylistsAndMessages() {
        Track deleted = Track.builder().id(UUID.randomUUID()).build();
        Track kept = Track.builder().id(UUID.randomUUID()).build();
        Playlist playlist = Playlist.builder().tracks(new ArrayList<>(List.of(kept, deleted, kept))).build();
        when(playlistRepository.findAllContainingTrack(deleted.getId())).thenReturn(List.of(playlist));

        cleanup.deleteTrackReferences(deleted.getId());

        assertThat(playlist.getTracks()).containsExactly(kept, kept);
        verify(messageRepository).detachTrack(deleted.getId());
    }
}

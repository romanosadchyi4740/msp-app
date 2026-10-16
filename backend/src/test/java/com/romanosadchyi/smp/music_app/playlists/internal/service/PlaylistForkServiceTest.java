package com.romanosadchyi.smp.music_app.playlists.internal.service;

import com.romanosadchyi.smp.music_app.playlists.internal.dto.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.playlists.internal.dto.PlaylistSyncResponseDto;
import com.romanosadchyi.smp.music_app.playlists.internal.entity.Playlist;
import com.romanosadchyi.smp.music_app.playlists.internal.mapping.PlaylistMappingService;
import com.romanosadchyi.smp.music_app.playlists.internal.repository.PlaylistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaylistForkServiceTest {

    @Mock
    PlaylistRepository playlistRepository;

    @Mock
    PlaylistMappingService playlistMappingService;

    @InjectMocks
    PlaylistForkService playlistService;

    @Test
    void forkPlaylist_PlaylistIsPublic_CreatesNewPlaylist() {
        UUID userId = UUID.randomUUID();
        UUID originalId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        Playlist original = Playlist.builder()
                .id(originalId)
                .title("Original")
                .isPublic(true)
                .trackIds(new ArrayList<>(List.of(trackId)))
                .build();

        when(playlistRepository.findById(originalId)).thenReturn(Optional.of(original));
        when(playlistRepository.save(any(Playlist.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(playlistMappingService.map(any(Playlist.class))).thenAnswer(i -> {
            Playlist p = i.getArgument(0);
            return new PlaylistResponseDto(p.getId(), p.getTitle(), null, userId, false, null, null, 1, originalId, null, null);
        });

        PlaylistResponseDto response = playlistService.forkPlaylist(originalId, userId);

        assertNotNull(response);
        assertEquals("Original [Local Instance]", response.title());
        verify(playlistRepository).save(argThat(p ->
            p.getOwnerId().equals(userId) &&
            !p.isPublic() &&
            p.getForkedFrom().equals(original) &&
            p.getTrackIds().equals(List.of(trackId))
        ));
    }

    @Test
    void syncOrigin_AddsOnlyTracksMissingFromTheFork() {
        UUID userId = UUID.randomUUID();
        UUID shared = UUID.randomUUID();
        UUID added = UUID.randomUUID();
        Playlist origin = Playlist.builder().id(UUID.randomUUID()).trackIds(new ArrayList<>(List.of(shared, added))).build();
        Playlist fork = Playlist.builder().id(UUID.randomUUID()).ownerId(userId).forkedFrom(origin)
                .trackIds(new ArrayList<>(List.of(shared))).build();
        when(playlistRepository.findById(fork.getId())).thenReturn(Optional.of(fork));
        when(playlistRepository.save(fork)).thenReturn(fork);

        PlaylistSyncResponseDto response = playlistService.syncOrigin(fork.getId(), userId);

        assertEquals(1, response.newTracksAdded());
        assertEquals(List.of(shared, added), fork.getTrackIds());
    }

    @Test
    void syncOrigin_NotAFork_IsRejected() {
        UUID userId = UUID.randomUUID();
        Playlist playlist = Playlist.builder().id(UUID.randomUUID()).ownerId(userId).build();
        when(playlistRepository.findById(playlist.getId())).thenReturn(Optional.of(playlist));

        assertThrows(IllegalArgumentException.class, () -> playlistService.syncOrigin(playlist.getId(), userId));
    }
}

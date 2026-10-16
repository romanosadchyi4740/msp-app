package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistSyncResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Playlist;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.repository.PlaylistRepository;
import com.romanosadchyi.smp.music_app.service.mapping.PlaylistMappingService;
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
        Track track = Track.builder().id(UUID.randomUUID()).build();
        Playlist original = Playlist.builder()
                .id(originalId)
                .title("Original")
                .isPublic(true)
                .tracks(new ArrayList<>(List.of(track)))
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
            p.getTracks().size() == 1
        ));
    }

    @Test
    void syncOrigin_AddsOnlyTracksMissingFromTheFork() {
        UUID userId = UUID.randomUUID();
        Track shared = Track.builder().id(UUID.randomUUID()).build();
        Track added = Track.builder().id(UUID.randomUUID()).build();
        Playlist origin = Playlist.builder().id(UUID.randomUUID()).tracks(new ArrayList<>(List.of(shared, added))).build();
        Playlist fork = Playlist.builder().id(UUID.randomUUID()).ownerId(userId).forkedFrom(origin)
                .tracks(new ArrayList<>(List.of(shared))).build();
        when(playlistRepository.findById(fork.getId())).thenReturn(Optional.of(fork));
        when(playlistRepository.save(fork)).thenReturn(fork);

        PlaylistSyncResponseDto response = playlistService.syncOrigin(fork.getId(), userId);

        assertEquals(1, response.newTracksAdded());
        assertEquals(List.of(shared, added), fork.getTracks());
    }

    @Test
    void syncOrigin_NotAFork_IsRejected() {
        UUID userId = UUID.randomUUID();
        Playlist playlist = Playlist.builder().id(UUID.randomUUID()).ownerId(userId).build();
        when(playlistRepository.findById(playlist.getId())).thenReturn(Optional.of(playlist));

        assertThrows(IllegalArgumentException.class, () -> playlistService.syncOrigin(playlist.getId(), userId));
    }
}

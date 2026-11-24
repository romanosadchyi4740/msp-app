package com.romanosadchyi.smp.music_app.playlists.internal.service;

import com.romanosadchyi.smp.music_app.music.external.TrackLookup;
import com.romanosadchyi.smp.music_app.music.external.TrackNotFoundException;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.playlists.internal.dto.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.playlists.internal.entity.Playlist;
import com.romanosadchyi.smp.music_app.playlists.internal.mapping.PlaylistMappingService;
import com.romanosadchyi.smp.music_app.playlists.internal.repository.PlaylistRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
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
class PlaylistTrackServiceTest {

    @Mock
    PlaylistRepository playlistRepository;

    @Mock
    TrackLookup trackLookup;

    @Mock
    PlaylistMappingService playlistMappingService;

    @InjectMocks
    PlaylistTrackService playlistService;

    @Test
    void addTrackToPlaylist_UserIsOwner_AddsTrackSuccessfully() {
        UUID ownerId = UUID.randomUUID();
        UUID playlistId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();

        Playlist playlist = Playlist.builder().id(playlistId).ownerId(ownerId).trackIds(new ArrayList<>()).build();

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
        when(trackLookup.getTrack(trackId)).thenReturn(TrackResponseDto.builder().id(trackId).build());
        when(playlistRepository.save(any(Playlist.class))).thenReturn(playlist);
        when(playlistMappingService.map(playlist)).thenReturn(
            new PlaylistResponseDto(playlistId, null, null, ownerId, false, null, null, 1, null, null, null)
        );

        PlaylistResponseDto response = playlistService.addTrackToPlaylist(ownerId, playlistId, trackId);

        assertNotNull(response);
        assertEquals(List.of(trackId), playlist.getTrackIds());
        assertNull(playlist.getCoverUrl());
        verify(playlistRepository).save(playlist);
    }

    @Test
    void addTrackToPlaylist_PlaylistHasNoCover_UsesTheTrackCover() {
        UUID ownerId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        Playlist playlist = Playlist.builder().id(UUID.randomUUID()).ownerId(ownerId).build();
        when(playlistRepository.findById(playlist.getId())).thenReturn(Optional.of(playlist));
        when(trackLookup.getTrack(trackId)).thenReturn(TrackResponseDto.builder().id(trackId).coverUrl("cover").build());
        when(playlistRepository.save(playlist)).thenReturn(playlist);

        playlistService.addTrackToPlaylist(ownerId, playlist.getId(), trackId);

        assertEquals("cover", playlist.getCoverUrl());
    }

    @Test
    void addTrackToPlaylist_PlaylistHasACover_KeepsIt() {
        UUID ownerId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        Playlist playlist = Playlist.builder().id(UUID.randomUUID()).ownerId(ownerId).coverUrl("own").build();
        when(playlistRepository.findById(playlist.getId())).thenReturn(Optional.of(playlist));
        when(trackLookup.getTrack(trackId)).thenReturn(TrackResponseDto.builder().id(trackId).coverUrl("cover").build());
        when(playlistRepository.save(playlist)).thenReturn(playlist);

        playlistService.addTrackToPlaylist(ownerId, playlist.getId(), trackId);

        assertEquals("own", playlist.getCoverUrl());
    }

    @Test
    void addTrackToPlaylist_TrackMissing_IsNotFound() {
        UUID ownerId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        Playlist playlist = Playlist.builder().id(UUID.randomUUID()).ownerId(ownerId).build();
        when(playlistRepository.findById(playlist.getId())).thenReturn(Optional.of(playlist));
        when(trackLookup.getTrack(trackId)).thenThrow(new TrackNotFoundException("Track not found"));

        assertThrows(TrackNotFoundException.class,
                () -> playlistService.addTrackToPlaylist(ownerId, playlist.getId(), trackId));
        assertTrue(playlist.getTrackIds().isEmpty());
        verify(playlistRepository, never()).save(any());
    }

    @Test
    void addTrackToPlaylist_UserIsNotOwner_ThrowsForbidden() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID playlistId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();

        Playlist playlist = Playlist.builder().id(playlistId).ownerId(ownerId).build();

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));

        assertThrows(UserNotAuthorizedException.class, () -> playlistService.addTrackToPlaylist(otherUserId, playlistId, trackId));
    }

    @Test
    void removeTrackFromPlaylist_UserIsOwner_RemovesTheFirstOccurrence() {
        UUID ownerId = UUID.randomUUID();
        UUID playlistId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        Playlist playlist = Playlist.builder().id(playlistId).ownerId(ownerId)
                .trackIds(new ArrayList<>(List.of(trackId, other, trackId))).build();

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
        when(trackLookup.exists(trackId)).thenReturn(true);
        when(playlistRepository.save(any(Playlist.class))).thenReturn(playlist);
        when(playlistMappingService.map(playlist)).thenReturn(new PlaylistResponseDto(playlistId, null, null, ownerId, false, null, null, 0, null, null, null));

        PlaylistResponseDto response = playlistService.removeTrackFromPlaylist(ownerId, playlistId, trackId);

        assertNotNull(response);
        assertEquals(List.of(other, trackId), playlist.getTrackIds());
        verify(playlistRepository).save(playlist);
    }

    @Test
    void removeTrackFromPlaylist_TrackMissing_IsNotFound() {
        UUID ownerId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        Playlist playlist = Playlist.builder().id(UUID.randomUUID()).ownerId(ownerId).build();
        when(playlistRepository.findById(playlist.getId())).thenReturn(Optional.of(playlist));
        when(trackLookup.exists(trackId)).thenReturn(false);

        assertThrows(TrackNotFoundException.class,
                () -> playlistService.removeTrackFromPlaylist(ownerId, playlist.getId(), trackId));
        verify(playlistRepository, never()).save(any());
    }

    @Test
    void removeTrackFromPlaylist_UserIsNotOwner_ThrowsException() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID playlistId = UUID.randomUUID();

        Playlist playlist = Playlist.builder().id(playlistId).ownerId(ownerId).build();

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));

        assertThrows(UserNotAuthorizedException.class, () -> playlistService.removeTrackFromPlaylist(otherId, playlistId, UUID.randomUUID()));
    }
}

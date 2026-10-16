package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Playlist;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.music.internal.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.repository.PlaylistRepository;
import com.romanosadchyi.smp.music_app.service.mapping.PlaylistMappingService;
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
    TrackRepository trackRepository;

    @Mock
    PlaylistMappingService playlistMappingService;

    @InjectMocks
    PlaylistTrackService playlistService;

    @Test
    void addTrackToPlaylist_UserIsOwner_AddsTrackSuccessfully() {
        UUID ownerId = UUID.randomUUID();
        UUID playlistId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        
        Playlist playlist = Playlist.builder().id(playlistId).ownerId(ownerId).tracks(new ArrayList<>()).build();
        Track track = Track.builder().id(trackId).build();

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));
        when(playlistRepository.save(any(Playlist.class))).thenReturn(playlist);
        when(playlistMappingService.map(playlist)).thenReturn(
            new PlaylistResponseDto(playlistId, null, null, ownerId, false, null, null, 1, null, null, null)
        );

        PlaylistResponseDto response = playlistService.addTrackToPlaylist(ownerId, playlistId, trackId);

        assertNotNull(response);
        assertTrue(playlist.getTracks().contains(track));
        verify(playlistRepository).save(playlist);
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
    void removeTrackFromPlaylist_UserIsOwner_RemovesTrack() {
        UUID ownerId = UUID.randomUUID();
        UUID playlistId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        
        Track track = Track.builder().id(trackId).build();
        Playlist playlist = Playlist.builder().id(playlistId).ownerId(ownerId).tracks(new ArrayList<>(List.of(track))).build();

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));
        when(playlistRepository.save(any(Playlist.class))).thenReturn(playlist);
        when(playlistMappingService.map(playlist)).thenReturn(new PlaylistResponseDto(playlistId, null, null, ownerId, false, null, null, 0, null, null, null));

        PlaylistResponseDto response = playlistService.removeTrackFromPlaylist(ownerId, playlistId, trackId);

        assertNotNull(response);
        assertFalse(playlist.getTracks().contains(track));
        verify(playlistRepository).save(playlist);
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

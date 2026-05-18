package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistSyncResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Playlist;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.repository.PlaylistRepository;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.service.mapping.PlaylistMappingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    @Mock
    PlaylistRepository playlistRepository;

    @Mock
    TrackRepository trackRepository;

    @Mock
    PlaylistMappingService playlistMappingService;

    @InjectMocks
    PlaylistService playlistService;

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
        verify(trackRepository, never()).findById(any());
        verify(playlistRepository, never()).save(any(Playlist.class));
    }

    @Test
    void forkPlaylist_PlaylistIsPublic_CreatesNewPlaylist() {
        UUID userId = UUID.randomUUID();
        UUID originalId = UUID.randomUUID();
        Track track = Track.builder().id(UUID.randomUUID()).build();
        Playlist original = Playlist.builder()
                .id(originalId)
                .title("Original")
                .isPublic(true)
                .tracks(new ArrayList<>(java.util.List.of(track)))
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
    void syncOrigin_OriginHasNewTracks_AppendsDelta() {
        UUID userId = UUID.randomUUID();
        UUID localId = UUID.randomUUID();
        
        Track existingTrack = Track.builder().id(UUID.randomUUID()).build();
        Track newTrack = Track.builder().id(UUID.randomUUID()).build();
        
        Playlist origin = Playlist.builder().tracks(new ArrayList<>(java.util.List.of(existingTrack, newTrack))).build();
        Playlist local = Playlist.builder()
                .id(localId)
                .ownerId(userId)
                .forkedFrom(origin)
                .tracks(new ArrayList<>(java.util.List.of(existingTrack)))
                .build();

        when(playlistRepository.findById(localId)).thenReturn(Optional.of(local));
        when(playlistRepository.save(any(Playlist.class))).thenReturn(local);
        when(playlistMappingService.map(any(Playlist.class))).thenReturn(
            new PlaylistResponseDto(localId, null, null, userId, false, null, null, 2, null, null, null)
        );

        PlaylistSyncResponseDto response = playlistService.syncOrigin(localId, userId);

        assertNotNull(response);
        assertEquals(1, response.newTracksAdded());
        assertEquals(2, local.getTracks().size());
        assertTrue(local.getTracks().contains(newTrack));
        verify(playlistRepository).save(local);
    }

    @Test
    void syncOrigin_NoNewTracks_ReturnsEmptyDelta() {
        UUID userId = UUID.randomUUID();
        UUID localId = UUID.randomUUID();
        
        Track existingTrack = Track.builder().id(UUID.randomUUID()).build();
        
        Playlist origin = Playlist.builder().tracks(new ArrayList<>(java.util.List.of(existingTrack))).build();
        Playlist local = Playlist.builder()
                .id(localId)
                .ownerId(userId)
                .forkedFrom(origin)
                .tracks(new ArrayList<>(java.util.List.of(existingTrack)))
                .build();

        when(playlistRepository.findById(localId)).thenReturn(Optional.of(local));
        when(playlistMappingService.map(local)).thenReturn(
            new PlaylistResponseDto(localId, null, null, userId, false, null, null, 1, null, null, null)
        );

        PlaylistSyncResponseDto response = playlistService.syncOrigin(localId, userId);

        assertNotNull(response);
        assertEquals(0, response.newTracksAdded());
        verify(playlistRepository, never()).save(any(Playlist.class));
    }
}

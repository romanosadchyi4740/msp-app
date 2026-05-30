package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.model.dto.request.PlaylistCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.IntegrationRequestResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistSyncResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.IntegrationRequest;
import com.romanosadchyi.smp.music_app.model.entity.Playlist;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.enums.RequestStatus;
import com.romanosadchyi.smp.music_app.repository.IntegrationRequestRepository;
import com.romanosadchyi.smp.music_app.repository.PlaylistRepository;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
import com.romanosadchyi.smp.music_app.service.mapping.IntegrationMappingService;
import com.romanosadchyi.smp.music_app.service.mapping.PlaylistMappingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    UserRepository userRepository;

    @Mock
    IntegrationRequestRepository integrationRequestRepository;

    @Mock
    PlaylistMappingService playlistMappingService;

    @Mock
    IntegrationMappingService integrationMappingService;

    @InjectMocks
    PlaylistService playlistService;

    @Test
    void createPlaylist_Success() {
        UUID userId = UUID.randomUUID();
        PlaylistCreateDto dto = new PlaylistCreateDto("Title", "Desc", true);
        
        when(playlistRepository.save(any(Playlist.class))).thenAnswer(i -> i.getArgument(0));
        when(playlistMappingService.map(any(Playlist.class))).thenReturn(new PlaylistResponseDto(null, "Title", "Desc", userId, true, null, null, 0, null, null, null));

        PlaylistResponseDto res = playlistService.createPlaylist(userId, dto);
        
        assertNotNull(res);
        assertEquals("Title", res.title());
        verify(playlistRepository).save(any(Playlist.class));
    }

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
    void getPlaylistById_PrivateAccessNotOwner_ThrowsException() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID playlistId = UUID.randomUUID();
        
        Playlist playlist = Playlist.builder().id(playlistId).ownerId(ownerId).isPublic(false).build();

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));

        assertThrows(UserNotAuthorizedException.class, () -> playlistService.getPlaylistById(playlistId, otherUserId));
    }

    @Test
    void toggleVisibility_UserIsOwner_Toggles() {
        UUID ownerId = UUID.randomUUID();
        UUID playlistId = UUID.randomUUID();
        Playlist playlist = Playlist.builder().id(playlistId).ownerId(ownerId).isPublic(false).build();

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
        when(playlistRepository.save(any(Playlist.class))).thenAnswer(i -> i.getArgument(0));

        playlistService.toggleVisibility(playlistId, ownerId);

        assertTrue(playlist.isPublic());
        verify(playlistRepository).save(playlist);
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
    void deletePlaylist_UserIsOwner_DetachesAndDeletes() {
        UUID ownerId = UUID.randomUUID();
        UUID playlistId = UUID.randomUUID();
        Playlist playlist = Playlist.builder().id(playlistId).ownerId(ownerId).build();

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));

        playlistService.deletePlaylist(playlistId, ownerId);

        verify(playlistRepository).detachParentPlaylist(playlist);
        verify(playlistRepository).delete(playlist);
    }

    @Test
    void proposeIntegration_HasDelta_CreatesRequest() {
        UUID forkId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        
        Track track1 = Track.builder().id(UUID.randomUUID()).build();
        Track newTrack = Track.builder().id(UUID.randomUUID()).build();
        
        Playlist origin = Playlist.builder().tracks(new ArrayList<>(java.util.List.of(track1))).build();
        Playlist fork = Playlist.builder().id(forkId).ownerId(requesterId).forkedFrom(origin)
                .tracks(new ArrayList<>(java.util.List.of(track1, newTrack))).build();
                
        User requester = User.builder().id(requesterId).build();

        when(playlistRepository.findById(forkId)).thenReturn(Optional.of(fork));
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(integrationRequestRepository.save(any(IntegrationRequest.class))).thenAnswer(i -> i.getArgument(0));

        playlistService.proposeIntegration(forkId, requesterId);

        verify(integrationRequestRepository).save(argThat(req -> 
            req.getProposedTracks().size() == 1 &&
            req.getProposedTracks().contains(newTrack) &&
            req.getStatus() == RequestStatus.PENDING
        ));
    }

    @Test
    void proposeIntegration_NoDelta_ThrowsException() {
        UUID forkId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        
        Track track1 = Track.builder().id(UUID.randomUUID()).build();
        
        Playlist origin = Playlist.builder().tracks(new ArrayList<>(java.util.List.of(track1))).build();
        Playlist fork = Playlist.builder().id(forkId).ownerId(requesterId).forkedFrom(origin)
                .tracks(new ArrayList<>(java.util.List.of(track1))).build();

        when(playlistRepository.findById(forkId)).thenReturn(Optional.of(fork));

        assertThrows(IllegalArgumentException.class, () -> playlistService.proposeIntegration(forkId, requesterId));
        verify(integrationRequestRepository, never()).save(any());
    }

    @Test
    void resolveIntegration_Accept_MergesTracks() {
        UUID requestId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        
        Track existingTrack = Track.builder().id(UUID.randomUUID()).build();
        Track proposedTrack = Track.builder().id(UUID.randomUUID()).build();
        
        Playlist target = Playlist.builder().ownerId(ownerId).tracks(new ArrayList<>(java.util.List.of(existingTrack))).build();
        IntegrationRequest request = IntegrationRequest.builder()
                .id(requestId)
                .status(RequestStatus.PENDING)
                .targetPlaylist(target)
                .proposedTracks(new HashSet<>(java.util.List.of(proposedTrack)))
                .build();

        when(integrationRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(integrationRequestRepository.save(any(IntegrationRequest.class))).thenReturn(request);

        playlistService.resolveIntegration(requestId, "ACCEPT", ownerId);

        assertEquals(RequestStatus.ACCEPTED, request.getStatus());
        assertTrue(target.getTracks().contains(proposedTrack));
        verify(playlistRepository).save(target);
    }

    @Test
    void resolveIntegration_Reject_UpdatesStatusOnly() {
        UUID requestId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        
        Playlist target = Playlist.builder().ownerId(ownerId).tracks(new ArrayList<>()).build();
        IntegrationRequest request = IntegrationRequest.builder()
                .id(requestId)
                .status(RequestStatus.PENDING)
                .targetPlaylist(target)
                .build();

        when(integrationRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(integrationRequestRepository.save(any(IntegrationRequest.class))).thenReturn(request);

        playlistService.resolveIntegration(requestId, "REJECT", ownerId);

        assertEquals(RequestStatus.REJECTED, request.getStatus());
        verify(playlistRepository, never()).save(any());
    }

    @Test
    void getMyPlaylists_ReturnsList() {
        UUID userId = UUID.randomUUID();
        when(playlistRepository.findAllByOwnerId(userId)).thenReturn(java.util.List.of(new Playlist()));
        
        List<PlaylistResponseDto> result = playlistService.getMyPlaylists(userId);
        
        assertEquals(1, result.size());
        verify(playlistRepository).findAllByOwnerId(userId);
    }

    @Test
    void getUserPublicPlaylists_ReturnsList() {
        UUID userId = UUID.randomUUID();
        when(playlistRepository.findAllByOwnerIdAndIsPublicTrue(userId)).thenReturn(java.util.List.of(new Playlist()));
        
        List<PlaylistResponseDto> result = playlistService.getUserPublicPlaylists(userId);
        
        assertEquals(1, result.size());
        verify(playlistRepository).findAllByOwnerIdAndIsPublicTrue(userId);
    }

    @Test
    void removeTrackFromPlaylist_UserIsOwner_RemovesTrack() {
        UUID ownerId = UUID.randomUUID();
        UUID playlistId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        
        Track track = Track.builder().id(trackId).build();
        Playlist playlist = Playlist.builder().id(playlistId).ownerId(ownerId).tracks(new ArrayList<>(java.util.List.of(track))).build();

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

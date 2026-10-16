package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.entity.IntegrationRequest;
import com.romanosadchyi.smp.music_app.model.entity.Playlist;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.model.enums.RequestStatus;
import com.romanosadchyi.smp.music_app.repository.IntegrationRequestRepository;
import com.romanosadchyi.smp.music_app.repository.PlaylistRepository;
import com.romanosadchyi.smp.music_app.service.mapping.IntegrationMappingService;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
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
class IntegrationRequestServiceTest {

    @Mock
    PlaylistRepository playlistRepository;

    @Mock
    IntegrationRequestRepository integrationRequestRepository;

    @Mock
    UserLookup userLookup;

    @Mock
    IntegrationMappingService integrationMappingService;

    @InjectMocks
    IntegrationRequestService playlistService;

    @Test
    void proposeIntegration_HasDelta_CreatesRequest() {
        UUID forkId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        
        Track track1 = Track.builder().id(UUID.randomUUID()).build();
        Track newTrack = Track.builder().id(UUID.randomUUID()).build();
        
        Playlist origin = Playlist.builder().tracks(new ArrayList<>(List.of(track1))).build();
        Playlist fork = Playlist.builder().id(forkId).ownerId(requesterId).forkedFrom(origin)
                .tracks(new ArrayList<>(List.of(track1, newTrack))).build();
                

        when(playlistRepository.findById(forkId)).thenReturn(Optional.of(fork));
        when(userLookup.exists(requesterId)).thenReturn(true);
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
        
        Playlist origin = Playlist.builder().tracks(new ArrayList<>(List.of(track1))).build();
        Playlist fork = Playlist.builder().id(forkId).ownerId(requesterId).forkedFrom(origin)
                .tracks(new ArrayList<>(List.of(track1))).build();

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
        
        Playlist target = Playlist.builder().ownerId(ownerId).tracks(new ArrayList<>(List.of(existingTrack))).build();
        IntegrationRequest request = IntegrationRequest.builder()
                .id(requestId)
                .status(RequestStatus.PENDING)
                .targetPlaylist(target)
                .proposedTracks(new HashSet<>(List.of(proposedTrack)))
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
}

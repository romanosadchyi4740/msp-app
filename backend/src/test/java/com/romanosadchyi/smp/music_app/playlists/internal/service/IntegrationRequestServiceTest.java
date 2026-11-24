package com.romanosadchyi.smp.music_app.playlists.internal.service;

import com.romanosadchyi.smp.music_app.playlists.internal.entity.IntegrationRequest;
import com.romanosadchyi.smp.music_app.playlists.internal.entity.Playlist;
import com.romanosadchyi.smp.music_app.playlists.internal.entity.RequestStatus;
import com.romanosadchyi.smp.music_app.playlists.internal.mapping.IntegrationMappingService;
import com.romanosadchyi.smp.music_app.playlists.internal.repository.IntegrationRequestRepository;
import com.romanosadchyi.smp.music_app.playlists.internal.repository.PlaylistRepository;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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

        UUID track1 = UUID.randomUUID();
        UUID newTrack1 = UUID.randomUUID();
        UUID newTrack2 = UUID.randomUUID();

        Playlist origin = Playlist.builder().trackIds(new ArrayList<>(List.of(track1))).build();
        Playlist fork = Playlist.builder().id(forkId).ownerId(requesterId).forkedFrom(origin)
                .trackIds(new ArrayList<>(List.of(newTrack1, track1, newTrack2))).build();

        when(playlistRepository.findById(forkId)).thenReturn(Optional.of(fork));
        when(userLookup.exists(requesterId)).thenReturn(true);
        when(integrationRequestRepository.save(any(IntegrationRequest.class))).thenAnswer(i -> i.getArgument(0));

        playlistService.proposeIntegration(forkId, requesterId);

        verify(integrationRequestRepository).save(argThat(req ->
            List.copyOf(req.getProposedTrackIds()).equals(List.of(newTrack1, newTrack2)) &&
            req.getTargetPlaylist() == origin &&
            req.getStatus() == RequestStatus.PENDING
        ));
    }

    @Test
    void proposeIntegration_NoDelta_ThrowsException() {
        UUID forkId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        UUID track1 = UUID.randomUUID();

        Playlist origin = Playlist.builder().trackIds(new ArrayList<>(List.of(track1))).build();
        Playlist fork = Playlist.builder().id(forkId).ownerId(requesterId).forkedFrom(origin)
                .trackIds(new ArrayList<>(List.of(track1))).build();

        when(playlistRepository.findById(forkId)).thenReturn(Optional.of(fork));

        assertThrows(IllegalArgumentException.class, () -> playlistService.proposeIntegration(forkId, requesterId));
        verify(integrationRequestRepository, never()).save(any());
    }

    @Test
    void resolveIntegration_Accept_MergesTracks() {
        UUID requestId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        UUID existingTrack = UUID.randomUUID();
        UUID proposedTrack = UUID.randomUUID();

        Playlist target = Playlist.builder().ownerId(ownerId).trackIds(new ArrayList<>(List.of(existingTrack))).build();
        IntegrationRequest request = IntegrationRequest.builder()
                .id(requestId)
                .status(RequestStatus.PENDING)
                .targetPlaylist(target)
                .proposedTrackIds(new LinkedHashSet<>(List.of(existingTrack, proposedTrack)))
                .build();

        when(integrationRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(integrationRequestRepository.save(any(IntegrationRequest.class))).thenReturn(request);

        playlistService.resolveIntegration(requestId, "ACCEPT", ownerId);

        assertEquals(RequestStatus.ACCEPTED, request.getStatus());
        assertEquals(List.of(existingTrack, proposedTrack), target.getTrackIds());
        verify(playlistRepository).save(target);
    }

    @Test
    void resolveIntegration_Reject_UpdatesStatusOnly() {
        UUID requestId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        Playlist target = Playlist.builder().ownerId(ownerId).build();
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

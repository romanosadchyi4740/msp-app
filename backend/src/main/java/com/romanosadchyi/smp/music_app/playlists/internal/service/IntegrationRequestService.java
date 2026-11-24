package com.romanosadchyi.smp.music_app.playlists.internal.service;

import com.romanosadchyi.smp.music_app.playlists.internal.dto.IntegrationRequestResponseDto;
import com.romanosadchyi.smp.music_app.playlists.internal.entity.IntegrationRequest;
import com.romanosadchyi.smp.music_app.playlists.internal.entity.Playlist;
import com.romanosadchyi.smp.music_app.playlists.internal.entity.RequestStatus;
import com.romanosadchyi.smp.music_app.playlists.internal.exception.PlaylistNotFoundException;
import com.romanosadchyi.smp.music_app.playlists.internal.mapping.IntegrationMappingService;
import com.romanosadchyi.smp.music_app.playlists.internal.repository.IntegrationRequestRepository;
import com.romanosadchyi.smp.music_app.playlists.internal.repository.PlaylistRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Integration requests: a fork owner proposes the fork's new tracks to the origin, whose owner accepts or rejects them.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IntegrationRequestService {

    PlaylistRepository playlistRepository;
    IntegrationRequestRepository integrationRequestRepository;
    UserLookup userLookup;
    IntegrationMappingService integrationMappingService;

    @Transactional
    public IntegrationRequestResponseDto proposeIntegration(UUID forkId, UUID requesterId) {
        Playlist fork = findPlaylist(forkId, "Forked playlist not found");
        requireOwner(fork, requesterId, "Only the fork owner can propose integration");
        Playlist origin = requireOrigin(fork);

        Set<UUID> deltaTracks = tracksMissingFrom(origin, fork.getTrackIds());
        if (deltaTracks.isEmpty()) {
            throw new IllegalArgumentException("No new transmissions to propose");
        }

        if (!userLookup.exists(requesterId)) {
            throw new UserNotFoundException("Requester not found");
        }

        IntegrationRequest request = IntegrationRequest.builder()
                .targetPlaylist(origin)
                .requesterId(requesterId)
                .proposedTrackIds(deltaTracks)
                .status(RequestStatus.PENDING)
                .build();

        return integrationMappingService.map(integrationRequestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public List<IntegrationRequestResponseDto> getPendingIntegrations(UUID originId, UUID ownerId) {
        Playlist origin = findPlaylist(originId, "Playlist not found");
        requireOwner(origin, ownerId, "Only the playlist owner can view integration requests");

        return integrationMappingService.mapAll(
                integrationRequestRepository.findAllByTargetPlaylistIdAndStatusOrderByCreatedAtDesc(originId, RequestStatus.PENDING));
    }

    @Transactional
    public IntegrationRequestResponseDto resolveIntegration(UUID requestId, String action, UUID ownerId) {
        IntegrationRequest request = integrationRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Integration request not found"));
        requireOwner(request.getTargetPlaylist(), ownerId, "Only the playlist owner can resolve integration requests");

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalArgumentException("Request is already resolved");
        }

        if ("ACCEPT".equalsIgnoreCase(action)) {
            accept(request);
        } else if ("REJECT".equalsIgnoreCase(action)) {
            request.setStatus(RequestStatus.REJECTED);
        } else {
            throw new IllegalArgumentException("Invalid action. Must be ACCEPT or REJECT");
        }

        return integrationMappingService.map(integrationRequestRepository.save(request));
    }

    private void accept(IntegrationRequest request) {
        Playlist targetPlaylist = request.getTargetPlaylist();
        targetPlaylist.getTrackIds().addAll(tracksMissingFrom(targetPlaylist, request.getProposedTrackIds()));
        playlistRepository.save(targetPlaylist);
        request.setStatus(RequestStatus.ACCEPTED);
    }

    private Playlist findPlaylist(UUID playlistId, String notFoundMessage) {
        return playlistRepository.findById(playlistId)
                .orElseThrow(() -> new PlaylistNotFoundException(notFoundMessage));
    }

    private static void requireOwner(Playlist playlist, UUID userId, String message) {
        if (!playlist.getOwnerId().equals(userId)) {
            throw new UserNotAuthorizedException(message);
        }
    }

    private static Playlist requireOrigin(Playlist fork) {
        Playlist origin = fork.getForkedFrom();
        if (origin == null) {
            throw new IllegalArgumentException("This playlist is not a fork, cannot propose integration");
        }
        return origin;
    }

    /**
     * The given tracks that the playlist does not contain yet.
     */
    private static Set<UUID> tracksMissingFrom(Playlist playlist, Collection<UUID> trackIds) {
        Set<UUID> existingTrackIds = Set.copyOf(playlist.getTrackIds());

        return trackIds.stream()
                .filter(trackId -> !existingTrackIds.contains(trackId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}

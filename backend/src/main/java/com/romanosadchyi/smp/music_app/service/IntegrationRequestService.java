package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.PlaylistNotFoundException;
import com.romanosadchyi.smp.music_app.model.dto.response.IntegrationRequestResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.IntegrationRequest;
import com.romanosadchyi.smp.music_app.model.entity.Playlist;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.model.enums.RequestStatus;
import com.romanosadchyi.smp.music_app.repository.IntegrationRequestRepository;
import com.romanosadchyi.smp.music_app.repository.PlaylistRepository;
import com.romanosadchyi.smp.music_app.service.mapping.IntegrationMappingService;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        Playlist fork = playlistRepository.findById(forkId)
                .orElseThrow(() -> new PlaylistNotFoundException("Forked playlist not found"));

        if (!fork.getOwnerId().equals(requesterId)) {
            throw new UserNotAuthorizedException("Only the fork owner can propose integration");
        }

        Playlist origin = fork.getForkedFrom();
        if (origin == null) {
            throw new IllegalArgumentException("This playlist is not a fork, cannot propose integration");
        }

        Set<UUID> originTrackIds = origin.getTracks().stream()
                .map(Track::getId)
                .collect(Collectors.toSet());

        Set<Track> deltaTracks = fork.getTracks().stream()
                .filter(track -> !originTrackIds.contains(track.getId()))
                .collect(Collectors.toSet());

        if (deltaTracks.isEmpty()) {
            throw new IllegalArgumentException("No new transmissions to propose");
        }

        if (!userLookup.exists(requesterId)) {
            throw new UserNotFoundException("Requester not found");
        }

        IntegrationRequest request = IntegrationRequest.builder()
                .targetPlaylist(origin)
                .requesterId(requesterId)
                .proposedTracks(deltaTracks)
                .status(RequestStatus.PENDING)
                .build();

        return integrationMappingService.map(integrationRequestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public List<IntegrationRequestResponseDto> getPendingIntegrations(UUID originId, UUID ownerId) {
        Playlist origin = playlistRepository.findById(originId)
                .orElseThrow(() -> new PlaylistNotFoundException("Playlist not found"));

        if (!origin.getOwnerId().equals(ownerId)) {
            throw new UserNotAuthorizedException("Only the playlist owner can view integration requests");
        }

        return integrationRequestRepository.findAllByTargetPlaylistIdAndStatusOrderByCreatedAtDesc(originId, RequestStatus.PENDING)
                .stream()
                .map(integrationMappingService::map)
                .collect(Collectors.toList());
    }

    @Transactional
    public IntegrationRequestResponseDto resolveIntegration(UUID requestId, String action, UUID ownerId) {
        IntegrationRequest request = integrationRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Integration request not found"));

        Playlist targetPlaylist = request.getTargetPlaylist();

        if (!targetPlaylist.getOwnerId().equals(ownerId)) {
            throw new UserNotAuthorizedException("Only the playlist owner can resolve integration requests");
        }

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalArgumentException("Request is already resolved");
        }

        if ("ACCEPT".equalsIgnoreCase(action)) {
            Set<UUID> existingTrackIds = targetPlaylist.getTracks().stream()
                    .map(Track::getId)
                    .collect(Collectors.toSet());

            for (Track track : request.getProposedTracks()) {
                if (!existingTrackIds.contains(track.getId())) {
                    targetPlaylist.getTracks().add(track);
                }
            }
            playlistRepository.save(targetPlaylist);
            request.setStatus(RequestStatus.ACCEPTED);
        } else if ("REJECT".equalsIgnoreCase(action)) {
            request.setStatus(RequestStatus.REJECTED);
        } else {
            throw new IllegalArgumentException("Invalid action. Must be ACCEPT or REJECT");
        }

        return integrationMappingService.map(integrationRequestRepository.save(request));
    }
}

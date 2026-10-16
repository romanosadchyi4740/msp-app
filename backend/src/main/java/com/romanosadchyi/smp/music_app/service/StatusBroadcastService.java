package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.TrackNotFoundException;
import com.romanosadchyi.smp.music_app.model.dto.request.ConsensusPollRequest;
import com.romanosadchyi.smp.music_app.model.dto.request.TelemetryBroadcastRequest;
import com.romanosadchyi.smp.music_app.model.dto.response.StatusBroadcastResponse;
import com.romanosadchyi.smp.music_app.model.entity.ConsensusPoll;
import com.romanosadchyi.smp.music_app.model.entity.PollOption;
import com.romanosadchyi.smp.music_app.model.entity.StatusBroadcast;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.repository.StatusBroadcastRepository;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.service.mapping.TelemetryMappingService;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Status broadcasts: posting (with an optional track and poll), the feed of followed users, and deletion.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StatusBroadcastService {

    StatusBroadcastRepository broadcastRepository;
    TrackRepository trackRepository;
    UserLookup userLookup;
    TelemetryMappingService telemetryMappingService;

    @Transactional
    public StatusBroadcastResponse createBroadcast(TelemetryBroadcastRequest request, UUID authorId) {
        if (!userLookup.exists(authorId)) {
            throw new UserNotFoundException("User not found");
        }

        Track attachedTrack = null;
        if (request.attachedTrackId() != null) {
            attachedTrack = trackRepository.findById(request.attachedTrackId())
                    .orElseThrow(() -> new TrackNotFoundException("Track not found"));
        }

        StatusBroadcast broadcast = StatusBroadcast.builder()
                .authorId(authorId)
                .content(request.content())
                .attachedTrack(attachedTrack)
                .timestamp(Instant.now())
                .build();

        if (request.poll() != null) {
            broadcast.setPoll(buildPoll(broadcast, request.poll()));
        }

        return telemetryMappingService.map(broadcastRepository.save(broadcast), authorId);
    }

    @Transactional(readOnly = true)
    public Page<StatusBroadcastResponse> getFeed(UUID currentUserId, Pageable pageable) {
        Set<UUID> authorIds = new HashSet<>(userLookup.findFollowingIds(currentUserId));
        authorIds.add(currentUserId);
        return broadcastRepository.findAllByAuthorIds(authorIds, pageable)
                .map(broadcast -> telemetryMappingService.map(broadcast, currentUserId));
    }

    @Transactional
    public void deleteBroadcast(UUID broadcastId, UUID currentUserId) {
        StatusBroadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new RuntimeException("Broadcast not found"));

        if (!broadcast.getAuthorId().equals(currentUserId)) {
            throw new RuntimeException("User is not authorized to delete this broadcast");
        }

        broadcastRepository.delete(broadcast);
    }

    private static ConsensusPoll buildPoll(StatusBroadcast broadcast, ConsensusPollRequest request) {
        ConsensusPoll poll = ConsensusPoll.builder()
                .broadcast(broadcast)
                .question(request.question())
                .build();
        poll.setOptions(request.options().stream()
                .map(option -> PollOption.builder()
                        .poll(poll)
                        .optionText(option)
                        .build())
                .collect(Collectors.toCollection(ArrayList::new)));
        return poll;
    }
}

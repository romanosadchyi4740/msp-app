package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.TrackNotFoundException;
import com.romanosadchyi.smp.music_app.model.dto.request.TelemetryBroadcastRequest;
import com.romanosadchyi.smp.music_app.model.dto.response.*;
import com.romanosadchyi.smp.music_app.model.entity.*;
import com.romanosadchyi.smp.music_app.repository.*;
import com.romanosadchyi.smp.music_app.service.mapping.TrackMappingService;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TelemetryService {

    private final StatusBroadcastRepository broadcastRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;
    private final UserLookup userLookup;
    private final TrackRepository trackRepository;

    private final TrackMappingService trackMappingService;

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
            ConsensusPoll poll = ConsensusPoll.builder()
                    .broadcast(broadcast)
                    .question(request.poll().question())
                    .build();

            List<PollOption> options = request.poll().options().stream()
                    .map(opt -> PollOption.builder()
                            .poll(poll)
                            .optionText(opt)
                            .build())
                    .collect(Collectors.toList());

            poll.setOptions(options);
            broadcast.setPoll(poll);
        }

        StatusBroadcast savedBroadcast = broadcastRepository.save(broadcast);
        return mapToResponse(savedBroadcast, authorId);
    }

    @Transactional(readOnly = true)
    public Page<StatusBroadcastResponse> getFeed(UUID currentUserId, Pageable pageable) {
        Set<UUID> authorIds = new HashSet<>(userLookup.findFollowingIds(currentUserId));
        authorIds.add(currentUserId);
        Page<StatusBroadcast> broadcasts = broadcastRepository.findAllByAuthorIds(authorIds, pageable);
        return broadcasts.map(b -> mapToResponse(b, currentUserId));
    }

    @Transactional
    public ConsensusPollResponse voteOnOption(UUID optionId, UUID voterId) {
        if (!userLookup.exists(voterId)) {
            throw new UserNotFoundException("User not found");
        }

        PollOption selectedOption = pollOptionRepository.findById(optionId)
                .orElseThrow(() -> new RuntimeException("Poll option not found"));

        ConsensusPoll poll = selectedOption.getPoll();

        Optional<PollVote> existingVoteOpt = pollVoteRepository.findByPollIdAndVoterId(poll.getId(), voterId);

        if (existingVoteOpt.isPresent()) {
            PollVote existingVote = existingVoteOpt.get();
            if (existingVote.getOption().getId().equals(optionId)) {
                // Vote exists for exactly this option -> Revoke vote
                pollVoteRepository.delete(existingVote);
                existingVote.getOption().getVotes().remove(existingVote); // Maintain bidir integrity locally if needed
            } else {
                // Vote exists for a different option -> Switch vote
                existingVote.getOption().getVotes().remove(existingVote); // Remove from old option
                existingVote.setOption(selectedOption);
                selectedOption.getVotes().add(existingVote); // Add to new option
                pollVoteRepository.save(existingVote);
            }
        } else {
            // No vote exists -> Create new vote
            PollVote vote = PollVote.builder()
                    .option(selectedOption)
                    .voterId(voterId)
                    .build();
            selectedOption.getVotes().add(vote);
            pollVoteRepository.save(vote);
        }

        // Return updated poll
        return mapPollToResponse(poll, voterId);
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

    private StatusBroadcastResponse mapToResponse(StatusBroadcast broadcast, UUID currentUserId) {
        UserResponseDto authorDto = userLookup.getProfile(broadcast.getAuthorId());
        
        TrackResponseDto trackDto = null;
        if (broadcast.getAttachedTrack() != null) {
            trackDto = trackMappingService.map(broadcast.getAttachedTrack());
        }

        ConsensusPollResponse pollDto = null;
        if (broadcast.getPoll() != null) {
            pollDto = mapPollToResponse(broadcast.getPoll(), currentUserId);
        }

        return new StatusBroadcastResponse(
                broadcast.getId(),
                authorDto,
                broadcast.getContent(),
                trackDto,
                broadcast.getTimestamp(),
                pollDto
        );
    }

    private ConsensusPollResponse mapPollToResponse(ConsensusPoll poll, UUID currentUserId) {
        List<PollOptionResponse> optionResponses = poll.getOptions().stream()
                .map(opt -> new PollOptionResponse(opt.getId(), opt.getOptionText(), opt.getVotes() != null ? opt.getVotes().size() : 0))
                .collect(Collectors.toList());

        boolean hasVoted = false;
        UUID votedOptionId = null;

        for (PollOption opt : poll.getOptions()) {
            if (opt.getVotes() != null) {
                Optional<PollVote> userVote = opt.getVotes().stream()
                        .filter(v -> v.getVoterId().equals(currentUserId))
                        .findFirst();
                if (userVote.isPresent()) {
                    hasVoted = true;
                    votedOptionId = opt.getId();
                    break;
                }
            }
        }

        return new ConsensusPollResponse(
                poll.getId(),
                poll.getQuestion(),
                optionResponses,
                hasVoted,
                votedOptionId
        );
    }
}

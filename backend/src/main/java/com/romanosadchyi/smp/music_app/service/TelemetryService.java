package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.TrackNotFoundException;
import com.romanosadchyi.smp.music_app.exception.UserNotFoundException;
import com.romanosadchyi.smp.music_app.model.dto.request.TelemetryBroadcastRequest;
import com.romanosadchyi.smp.music_app.model.dto.response.*;
import com.romanosadchyi.smp.music_app.model.entity.*;
import com.romanosadchyi.smp.music_app.repository.*;
import com.romanosadchyi.smp.music_app.service.mapping.TrackMappingService;
import com.romanosadchyi.smp.music_app.service.mapping.UserMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TelemetryService {

    private final StatusBroadcastRepository broadcastRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;
    private final UserRepository userRepository;
    private final TrackRepository trackRepository;
    
    private final UserMappingService userMappingService;
    private final TrackMappingService trackMappingService;

    @Transactional
    public StatusBroadcastResponse createBroadcast(TelemetryBroadcastRequest request, UUID authorId) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        Track attachedTrack = null;
        if (request.attachedTrackId() != null) {
            attachedTrack = trackRepository.findById(request.attachedTrackId())
                    .orElseThrow(() -> new TrackNotFoundException("Track not found"));
        }

        StatusBroadcast broadcast = StatusBroadcast.builder()
                .author(author)
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
        Page<StatusBroadcast> broadcasts = broadcastRepository.findFeedForUser(currentUserId, pageable);
        return broadcasts.map(b -> mapToResponse(b, currentUserId));
    }

    @Transactional
    public void voteOnOption(UUID optionId, UUID voterId) {
        User voter = userRepository.findById(voterId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        PollOption option = pollOptionRepository.findById(optionId)
                .orElseThrow(() -> new RuntimeException("Poll option not found"));

        ConsensusPoll poll = option.getPoll();
        
        long existingVotes = pollVoteRepository.countByPollIdAndVoterId(poll.getId(), voterId);
        if (existingVotes > 0) {
            throw new RuntimeException("User has already voted in this consensus module.");
        }

        PollVote vote = PollVote.builder()
                .option(option)
                .voter(voter)
                .build();

        pollVoteRepository.save(vote);
    }

    private StatusBroadcastResponse mapToResponse(StatusBroadcast broadcast, UUID currentUserId) {
        UserResponseDto authorDto = userMappingService.map(broadcast.getAuthor());
        
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
                        .filter(v -> v.getVoter().getId().equals(currentUserId))
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

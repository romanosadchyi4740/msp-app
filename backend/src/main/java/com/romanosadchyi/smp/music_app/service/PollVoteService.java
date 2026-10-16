package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.dto.response.ConsensusPollResponse;
import com.romanosadchyi.smp.music_app.model.entity.ConsensusPoll;
import com.romanosadchyi.smp.music_app.model.entity.PollOption;
import com.romanosadchyi.smp.music_app.model.entity.PollVote;
import com.romanosadchyi.smp.music_app.repository.PollOptionRepository;
import com.romanosadchyi.smp.music_app.repository.PollVoteRepository;
import com.romanosadchyi.smp.music_app.service.mapping.TelemetryMappingService;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Voting on broadcast polls. A user has at most one vote per poll: voting for the same option again revokes it,
 * voting for another option moves it.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PollVoteService {

    PollOptionRepository pollOptionRepository;
    PollVoteRepository pollVoteRepository;
    UserLookup userLookup;
    TelemetryMappingService telemetryMappingService;

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
                existingVote.getOption().getVotes().remove(existingVote);
            } else {
                // Vote exists for a different option -> Switch vote
                existingVote.getOption().getVotes().remove(existingVote);
                existingVote.setOption(selectedOption);
                selectedOption.getVotes().add(existingVote);
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

        return telemetryMappingService.map(poll, voterId);
    }
}

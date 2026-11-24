package com.romanosadchyi.smp.music_app.telemetry.internal.service;

import com.romanosadchyi.smp.music_app.telemetry.internal.dto.ConsensusPollResponse;
import com.romanosadchyi.smp.music_app.telemetry.internal.entity.ConsensusPoll;
import com.romanosadchyi.smp.music_app.telemetry.internal.entity.PollOption;
import com.romanosadchyi.smp.music_app.telemetry.internal.entity.PollVote;
import com.romanosadchyi.smp.music_app.telemetry.internal.exception.PollOptionNotFoundException;
import com.romanosadchyi.smp.music_app.telemetry.internal.mapping.TelemetryMappingService;
import com.romanosadchyi.smp.music_app.telemetry.internal.repository.PollOptionRepository;
import com.romanosadchyi.smp.music_app.telemetry.internal.repository.PollVoteRepository;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PollVoteServiceTest {

    @Mock
    PollOptionRepository pollOptionRepository;

    @Mock
    PollVoteRepository pollVoteRepository;

    @Mock
    UserLookup userLookup;

    @Mock
    TelemetryMappingService telemetryMappingService;

    @InjectMocks
    PollVoteService pollVoteService;

    final UUID voterId = UUID.randomUUID();
    ConsensusPoll poll;
    PollOption yes;
    PollOption no;

    @BeforeEach
    void setUp() {
        poll = ConsensusPoll.builder().id(UUID.randomUUID()).question("Question?").build();
        yes = PollOption.builder().id(UUID.randomUUID()).poll(poll).optionText("Yes").build();
        no = PollOption.builder().id(UUID.randomUUID()).poll(poll).optionText("No").build();
        poll.setOptions(new ArrayList<>(List.of(yes, no)));
    }

    @Test
    void firstVote_CreatesVote() {
        ConsensusPollResponse response = new ConsensusPollResponse(poll.getId(), "Question?", List.of(), true, yes.getId());
        givenVoterAndOption(yes);
        when(pollVoteRepository.findByPollIdAndVoterId(poll.getId(), voterId)).thenReturn(Optional.empty());
        when(telemetryMappingService.map(poll, voterId)).thenReturn(response);

        assertThat(pollVoteService.voteOnOption(yes.getId(), voterId)).isEqualTo(response);

        assertThat(yes.getVotes()).singleElement().satisfies(v -> assertThat(v.getVoterId()).isEqualTo(voterId));
        verify(pollVoteRepository).save(yes.getVotes().getFirst());
    }

    @Test
    void sameOptionAgain_RevokesVote() {
        PollVote vote = existingVote(yes);
        givenVoterAndOption(yes);

        pollVoteService.voteOnOption(yes.getId(), voterId);

        verify(pollVoteRepository).delete(vote);
        assertThat(yes.getVotes()).isEmpty();
    }

    @Test
    void otherOption_MovesVote() {
        PollVote vote = existingVote(yes);
        givenVoterAndOption(no);

        pollVoteService.voteOnOption(no.getId(), voterId);

        assertThat(vote.getOption()).isEqualTo(no);
        assertThat(yes.getVotes()).isEmpty();
        assertThat(no.getVotes()).containsExactly(vote);
        verify(pollVoteRepository).save(vote);
        verify(pollVoteRepository, never()).delete(any());
    }

    @Test
    void unknownUser_Throws() {
        when(userLookup.exists(voterId)).thenReturn(false);

        assertThatThrownBy(() -> pollVoteService.voteOnOption(yes.getId(), voterId))
                .isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(pollOptionRepository, pollVoteRepository);
    }

    @Test
    void unknownOption_IsNotFound() {
        UUID optionId = UUID.randomUUID();
        when(userLookup.exists(voterId)).thenReturn(true);
        when(pollOptionRepository.findById(optionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pollVoteService.voteOnOption(optionId, voterId))
                .isInstanceOf(PollOptionNotFoundException.class);
        verifyNoInteractions(pollVoteRepository);
    }

    private void givenVoterAndOption(PollOption option) {
        when(userLookup.exists(voterId)).thenReturn(true);
        when(pollOptionRepository.findById(option.getId())).thenReturn(Optional.of(option));
    }

    private PollVote existingVote(PollOption option) {
        PollVote vote = PollVote.builder().id(UUID.randomUUID()).option(option).voterId(voterId).build();
        option.getVotes().add(vote);
        when(pollVoteRepository.findByPollIdAndVoterId(poll.getId(), voterId)).thenReturn(Optional.of(vote));
        return vote;
    }
}

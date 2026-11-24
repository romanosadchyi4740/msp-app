package com.romanosadchyi.smp.music_app.telemetry.internal.service;

import com.romanosadchyi.smp.music_app.telemetry.internal.entity.PollOption;
import com.romanosadchyi.smp.music_app.telemetry.internal.entity.PollVote;
import com.romanosadchyi.smp.music_app.telemetry.internal.entity.StatusBroadcast;
import com.romanosadchyi.smp.music_app.telemetry.internal.repository.PollVoteRepository;
import com.romanosadchyi.smp.music_app.telemetry.internal.repository.StatusBroadcastRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryUserDataCleanupTest {

    @Mock
    PollVoteRepository pollVoteRepository;

    @Mock
    StatusBroadcastRepository broadcastRepository;

    @InjectMocks
    TelemetryUserDataCleanup cleanup;

    final UUID userId = UUID.randomUUID();

    @Test
    void deleteUserData_RemovesTheUsersVotesFromTheirOptions() {
        PollOption option = PollOption.builder().id(UUID.randomUUID()).build();
        PollVote own = PollVote.builder().option(option).voterId(userId).build();
        PollVote other = PollVote.builder().option(option).voterId(UUID.randomUUID()).build();
        option.getVotes().addAll(List.of(own, other));
        when(pollVoteRepository.findAllByVoterId(userId)).thenReturn(List.of(own));

        cleanup.deleteUserData(userId);

        assertThat(option.getVotes()).containsExactly(other);
    }

    @Test
    void deleteUserData_DeletesTheUsersBroadcastsAfterTheirVotes() {
        List<StatusBroadcast> broadcasts = List.of(StatusBroadcast.builder().id(UUID.randomUUID()).build());
        when(broadcastRepository.findAllByAuthorId(userId)).thenReturn(broadcasts);

        cleanup.deleteUserData(userId);

        InOrder order = inOrder(pollVoteRepository, broadcastRepository);
        order.verify(pollVoteRepository).findAllByVoterId(userId);
        order.verify(broadcastRepository).deleteAll(broadcasts);
        verify(broadcastRepository).findAllByAuthorId(userId);
    }
}

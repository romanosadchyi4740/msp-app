package com.romanosadchyi.smp.music_app.service.mapping;

import com.romanosadchyi.smp.music_app.model.dto.response.ConsensusPollResponse;
import com.romanosadchyi.smp.music_app.model.dto.response.StatusBroadcastResponse;
import com.romanosadchyi.smp.music_app.model.entity.ConsensusPoll;
import com.romanosadchyi.smp.music_app.model.entity.PollOption;
import com.romanosadchyi.smp.music_app.model.entity.PollVote;
import com.romanosadchyi.smp.music_app.model.entity.StatusBroadcast;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.music.internal.mapping.TrackMappingService;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryMappingServiceTest {

    @Mock
    UserLookup userLookup;

    @Mock
    TrackMappingService trackMappingService;

    @InjectMocks
    TelemetryMappingService telemetryMappingService;

    final UUID authorId = UUID.randomUUID();
    final UUID viewerId = UUID.randomUUID();

    @Test
    void mapBroadcast_WithTrackAndPoll_MapsEverything() {
        Track track = Track.builder().id(UUID.randomUUID()).build();
        ConsensusPoll poll = poll(option("Yes"), option("No"));
        StatusBroadcast broadcast = StatusBroadcast.builder()
                .id(UUID.randomUUID())
                .authorId(authorId)
                .content("hello")
                .attachedTrack(track)
                .timestamp(Instant.now())
                .poll(poll)
                .build();
        UserResponseDto author = UserResponseDto.builder().id(authorId).build();
        TrackResponseDto trackDto = TrackResponseDto.builder().id(track.getId()).build();
        when(userLookup.getProfile(authorId)).thenReturn(author);
        when(trackMappingService.map(track)).thenReturn(trackDto);

        StatusBroadcastResponse response = telemetryMappingService.map(broadcast, viewerId);

        assertThat(response.id()).isEqualTo(broadcast.getId());
        assertThat(response.author()).isEqualTo(author);
        assertThat(response.content()).isEqualTo("hello");
        assertThat(response.attachedTrack()).isEqualTo(trackDto);
        assertThat(response.timestamp()).isEqualTo(broadcast.getTimestamp());
        assertThat(response.poll().question()).isEqualTo("Question?");
    }

    @Test
    void mapBroadcast_WithoutTrackAndPoll_LeavesThemNull() {
        StatusBroadcast broadcast = StatusBroadcast.builder().id(UUID.randomUUID()).authorId(authorId).build();
        when(userLookup.getProfile(authorId)).thenReturn(UserResponseDto.builder().id(authorId).build());

        StatusBroadcastResponse response = telemetryMappingService.map(broadcast, viewerId);

        assertThat(response.attachedTrack()).isNull();
        assertThat(response.poll()).isNull();
        verifyNoInteractions(trackMappingService);
    }

    @Test
    void mapPoll_ViewerVoted_MarksTheirOptionAndCountsVotes() {
        PollOption yes = option("Yes");
        PollOption no = option("No");
        vote(yes, UUID.randomUUID());
        vote(no, UUID.randomUUID());
        vote(no, viewerId);

        ConsensusPollResponse response = telemetryMappingService.map(poll(yes, no), viewerId);

        assertThat(response.hasVoted()).isTrue();
        assertThat(response.votedOptionId()).isEqualTo(no.getId());
        assertThat(response.options()).extracting("optionText", "voteCount")
                .containsExactly(tuple("Yes", 1), tuple("No", 2));
    }

    @Test
    void mapPoll_ViewerDidNotVote_HasNoVotedOption() {
        PollOption yes = option("Yes");
        vote(yes, UUID.randomUUID());

        ConsensusPollResponse response = telemetryMappingService.map(poll(yes), viewerId);

        assertThat(response.hasVoted()).isFalse();
        assertThat(response.votedOptionId()).isNull();
    }

    private static ConsensusPoll poll(PollOption... options) {
        ConsensusPoll poll = ConsensusPoll.builder().id(UUID.randomUUID()).question("Question?").build();
        poll.setOptions(new ArrayList<>(List.of(options)));
        return poll;
    }

    private static PollOption option(String text) {
        return PollOption.builder().id(UUID.randomUUID()).optionText(text).build();
    }

    private static void vote(PollOption option, UUID voterId) {
        option.getVotes().add(PollVote.builder().option(option).voterId(voterId).build());
    }
}

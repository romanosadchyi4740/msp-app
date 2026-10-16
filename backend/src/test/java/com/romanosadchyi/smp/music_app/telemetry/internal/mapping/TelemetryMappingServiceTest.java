package com.romanosadchyi.smp.music_app.telemetry.internal.mapping;

import com.romanosadchyi.smp.music_app.music.external.TrackLookup;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.telemetry.internal.dto.ConsensusPollResponse;
import com.romanosadchyi.smp.music_app.telemetry.internal.dto.StatusBroadcastResponse;
import com.romanosadchyi.smp.music_app.telemetry.internal.entity.ConsensusPoll;
import com.romanosadchyi.smp.music_app.telemetry.internal.entity.PollOption;
import com.romanosadchyi.smp.music_app.telemetry.internal.entity.PollVote;
import com.romanosadchyi.smp.music_app.telemetry.internal.entity.StatusBroadcast;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryMappingServiceTest {

    @Mock
    UserLookup userLookup;

    @Mock
    TrackLookup trackLookup;

    @InjectMocks
    TelemetryMappingService telemetryMappingService;

    final UUID authorId = UUID.randomUUID();
    final UUID viewerId = UUID.randomUUID();

    @Test
    void mapBroadcast_WithTrackAndPoll_MapsEverything() {
        UUID trackId = UUID.randomUUID();
        ConsensusPoll poll = poll(option("Yes"), option("No"));
        StatusBroadcast broadcast = StatusBroadcast.builder()
                .id(UUID.randomUUID())
                .authorId(authorId)
                .content("hello")
                .attachedTrackId(trackId)
                .timestamp(Instant.now())
                .poll(poll)
                .build();
        UserResponseDto author = UserResponseDto.builder().id(authorId).build();
        TrackResponseDto trackDto = TrackResponseDto.builder().id(trackId).build();
        when(userLookup.findProfiles(anyCollection())).thenReturn(Map.of(authorId, author));
        when(trackLookup.findTracks(List.of(trackId))).thenReturn(List.of(trackDto));

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
        when(userLookup.findProfiles(anyCollection())).thenReturn(Map.of(authorId, UserResponseDto.builder().id(authorId).build()));

        StatusBroadcastResponse response = telemetryMappingService.map(broadcast, viewerId);

        assertThat(response.attachedTrack()).isNull();
        assertThat(response.poll()).isNull();
        verifyNoInteractions(trackLookup);
    }

    @Test
    void mapPage_LooksUpAttachedTracksOnceForThePage() {
        UUID shared = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        List<StatusBroadcast> broadcasts = List.of(
                StatusBroadcast.builder().id(UUID.randomUUID()).authorId(authorId).attachedTrackId(shared).build(),
                StatusBroadcast.builder().id(UUID.randomUUID()).authorId(authorId).build(),
                StatusBroadcast.builder().id(UUID.randomUUID()).authorId(authorId).attachedTrackId(other).build(),
                StatusBroadcast.builder().id(UUID.randomUUID()).authorId(authorId).attachedTrackId(shared).build());
        TrackResponseDto sharedDto = TrackResponseDto.builder().id(shared).build();
        TrackResponseDto otherDto = TrackResponseDto.builder().id(other).build();
        when(userLookup.findProfiles(anyCollection())).thenReturn(Map.of(authorId, UserResponseDto.builder().id(authorId).build()));
        when(trackLookup.findTracks(List.of(shared, other))).thenReturn(List.of(sharedDto, otherDto));

        Page<StatusBroadcastResponse> result = telemetryMappingService.mapPage(new PageImpl<>(broadcasts), viewerId);

        assertThat(result.getContent()).extracting(StatusBroadcastResponse::attachedTrack)
                .containsExactly(sharedDto, null, otherDto, sharedDto);
        assertThat(result.getContent()).extracting(broadcast -> broadcast.author().id()).containsOnly(authorId);
        verify(trackLookup).findTracks(anyCollection());
        verify(userLookup).findProfiles(anyCollection());
        verify(userLookup, never()).getProfile(any());
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

package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.TrackNotFoundException;
import com.romanosadchyi.smp.music_app.model.dto.request.ConsensusPollRequest;
import com.romanosadchyi.smp.music_app.model.dto.request.TelemetryBroadcastRequest;
import com.romanosadchyi.smp.music_app.model.dto.response.StatusBroadcastResponse;
import com.romanosadchyi.smp.music_app.model.entity.StatusBroadcast;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.repository.StatusBroadcastRepository;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.service.mapping.TelemetryMappingService;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatusBroadcastServiceTest {

    @Mock
    StatusBroadcastRepository broadcastRepository;

    @Mock
    TrackRepository trackRepository;

    @Mock
    UserLookup userLookup;

    @Mock
    TelemetryMappingService telemetryMappingService;

    @InjectMocks
    StatusBroadcastService statusBroadcastService;

    final UUID authorId = UUID.randomUUID();

    @Test
    void createBroadcast_WithTrackAndPoll_SavesAndMaps() {
        Track track = Track.builder().id(UUID.randomUUID()).build();
        TelemetryBroadcastRequest request = new TelemetryBroadcastRequest(
                "hello", track.getId(), new ConsensusPollRequest("Question?", List.of("Yes", "No")));
        StatusBroadcastResponse response = new StatusBroadcastResponse(null, null, "hello", null, null, null);
        when(userLookup.exists(authorId)).thenReturn(true);
        when(trackRepository.findById(track.getId())).thenReturn(Optional.of(track));
        when(broadcastRepository.save(any(StatusBroadcast.class))).thenAnswer(i -> i.getArgument(0));
        when(telemetryMappingService.map(any(StatusBroadcast.class), eq(authorId))).thenReturn(response);

        assertThat(statusBroadcastService.createBroadcast(request, authorId)).isEqualTo(response);

        ArgumentCaptor<StatusBroadcast> saved = ArgumentCaptor.forClass(StatusBroadcast.class);
        verify(broadcastRepository).save(saved.capture());
        StatusBroadcast broadcast = saved.getValue();
        assertThat(broadcast.getAuthorId()).isEqualTo(authorId);
        assertThat(broadcast.getContent()).isEqualTo("hello");
        assertThat(broadcast.getAttachedTrack()).isEqualTo(track);
        assertThat(broadcast.getTimestamp()).isNotNull();
        assertThat(broadcast.getPoll().getBroadcast()).isEqualTo(broadcast);
        assertThat(broadcast.getPoll().getQuestion()).isEqualTo("Question?");
        assertThat(broadcast.getPoll().getOptions()).extracting("optionText").containsExactly("Yes", "No");
        assertThat(broadcast.getPoll().getOptions()).allSatisfy(o -> assertThat(o.getPoll()).isEqualTo(broadcast.getPoll()));
    }

    @Test
    void createBroadcast_TextOnly_HasNoTrackOrPoll() {
        when(userLookup.exists(authorId)).thenReturn(true);
        when(broadcastRepository.save(any(StatusBroadcast.class))).thenAnswer(i -> i.getArgument(0));

        statusBroadcastService.createBroadcast(new TelemetryBroadcastRequest("hello", null, null), authorId);

        ArgumentCaptor<StatusBroadcast> saved = ArgumentCaptor.forClass(StatusBroadcast.class);
        verify(broadcastRepository).save(saved.capture());
        assertThat(saved.getValue().getAttachedTrack()).isNull();
        assertThat(saved.getValue().getPoll()).isNull();
    }

    @Test
    void createBroadcast_UnknownUser_Throws() {
        when(userLookup.exists(authorId)).thenReturn(false);

        assertThatThrownBy(() -> statusBroadcastService.createBroadcast(
                new TelemetryBroadcastRequest("hello", null, null), authorId))
                .isInstanceOf(UserNotFoundException.class);
        verify(broadcastRepository, never()).save(any());
    }

    @Test
    void createBroadcast_UnknownTrack_Throws() {
        UUID trackId = UUID.randomUUID();
        when(userLookup.exists(authorId)).thenReturn(true);
        when(trackRepository.findById(trackId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> statusBroadcastService.createBroadcast(
                new TelemetryBroadcastRequest("hello", trackId, null), authorId))
                .isInstanceOf(TrackNotFoundException.class);
        verify(broadcastRepository, never()).save(any());
    }

    @Test
    void getFeed_IncludesFollowedUsersAndSelf() {
        UUID followed = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        StatusBroadcast broadcast = StatusBroadcast.builder().id(UUID.randomUUID()).authorId(followed).build();
        StatusBroadcastResponse response = new StatusBroadcastResponse(broadcast.getId(), null, null, null, null, null);
        when(userLookup.findFollowingIds(authorId)).thenReturn(Set.of(followed));
        when(broadcastRepository.findAllByAuthorIds(any(), eq(pageable))).thenReturn(new PageImpl<>(List.of(broadcast)));
        when(telemetryMappingService.map(broadcast, authorId)).thenReturn(response);

        assertThat(statusBroadcastService.getFeed(authorId, pageable).getContent()).containsExactly(response);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> authorIds = ArgumentCaptor.forClass(Collection.class);
        verify(broadcastRepository).findAllByAuthorIds(authorIds.capture(), eq(pageable));
        assertThat(authorIds.getValue()).containsExactlyInAnyOrder(followed, authorId);
    }

    @Test
    void deleteBroadcast_ByAuthor_Deletes() {
        StatusBroadcast broadcast = StatusBroadcast.builder().id(UUID.randomUUID()).authorId(authorId).build();
        when(broadcastRepository.findById(broadcast.getId())).thenReturn(Optional.of(broadcast));

        statusBroadcastService.deleteBroadcast(broadcast.getId(), authorId);

        verify(broadcastRepository).delete(broadcast);
    }

    @Test
    void deleteBroadcast_ByAnotherUser_ThrowsAndKeepsIt() {
        StatusBroadcast broadcast = StatusBroadcast.builder().id(UUID.randomUUID()).authorId(authorId).build();
        when(broadcastRepository.findById(broadcast.getId())).thenReturn(Optional.of(broadcast));

        assertThatThrownBy(() -> statusBroadcastService.deleteBroadcast(broadcast.getId(), UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class);
        verify(broadcastRepository, never()).delete(any());
    }
}

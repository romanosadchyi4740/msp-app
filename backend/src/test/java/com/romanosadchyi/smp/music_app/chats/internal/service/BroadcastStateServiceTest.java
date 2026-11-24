package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.dto.BroadcastCommand;
import com.romanosadchyi.smp.music_app.chats.internal.dto.BroadcastStatus;
import com.romanosadchyi.smp.music_app.chats.internal.dto.BroadcastSyncMessage;
import com.romanosadchyi.smp.music_app.chats.internal.exception.BroadcastEndedException;
import com.romanosadchyi.smp.music_app.chats.internal.exception.BroadcastNotFoundException;
import com.romanosadchyi.smp.music_app.music.external.TrackLookup;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BroadcastStateServiceTest {

    @Mock
    BroadcastNotifier notifier;

    @Mock
    TrackLookup trackLookup;

    @Mock
    Clock clock;

    @InjectMocks
    BroadcastStateService service;

    @Captor
    ArgumentCaptor<BroadcastState> published;

    final UUID chatId = UUID.randomUUID();
    final UUID broadcastId = UUID.randomUUID();
    final UUID hostId = UUID.randomUUID();
    final UUID listenerId = UUID.randomUUID();
    final UUID trackId = UUID.randomUUID();
    long now = 1_000_000L;

    @BeforeEach
    void setUp() {
        lenient().when(clock.millis()).thenAnswer(invocation -> now);
        lenient().when(trackLookup.exists(any())).thenReturn(true);
    }

    @Test
    void start_PausedTrack_PublishesItPausedAtItsPosition() {
        // The reported case: the host's track was paused when the broadcast started
        service.start(chatId, broadcastId, hostId, "host-session", state(trackId, 60.0, false));

        verify(notifier).publish(published.capture());
        BroadcastState state = published.getValue();
        assertThat(state.hostId()).isEqualTo(hostId);
        assertThat(state.status()).isEqualTo(BroadcastStatus.ACTIVE);
        assertThat(state.trackId()).isEqualTo(trackId);
        assertThat(state.position()).isEqualTo(60.0);
        assertThat(state.playing()).isFalse();
        assertThat(state.anchorTime()).isEqualTo(now);
        assertThat(state.version()).isEqualTo(1);
    }

    @Test
    void start_WithoutTrack_StartsEmptyAndPaused() {
        service.start(chatId, broadcastId, hostId, "host-session", BroadcastSyncMessage.builder()
                .command(BroadcastCommand.START).build());

        verify(notifier).publish(published.capture());
        assertThat(published.getValue().trackId()).isNull();
        assertThat(published.getValue().playing()).isFalse();
        verifyNoInteractions(trackLookup);
    }

    @Test
    void update_ByHost_StampsTheServerTimeAndIncrementsTheVersion() {
        service.start(chatId, broadcastId, hostId, "host-session", state(trackId, 0.0, false));
        now += 5_000;

        service.update(chatId, broadcastId, hostId, "host-session", state(trackId, 12.5, true));

        verify(notifier, times(2)).publish(published.capture());
        BroadcastState updated = published.getValue();
        assertThat(updated.position()).isEqualTo(12.5);
        assertThat(updated.playing()).isTrue();
        assertThat(updated.anchorTime()).isEqualTo(now);
        assertThat(updated.version()).isEqualTo(2);
    }

    @Test
    void update_ByAnotherMember_IsRefusedAndChangesNothing() {
        service.start(chatId, broadcastId, hostId, "host-session", state(trackId, 30.0, false));
        clearInvocations(notifier);

        assertThatThrownBy(() -> service.update(chatId, broadcastId, listenerId, "listener-session",
                state(trackId, 0.0, true)))
                .isInstanceOf(UserNotAuthorizedException.class);

        verify(notifier, never()).publish(any());
        service.requestSync(chatId, broadcastId, listenerId);
        verify(notifier).sendSnapshot(eq(listenerId), eq(chatId), eq(broadcastId), published.capture());
        assertThat(published.getValue().playing()).isFalse();
        assertThat(published.getValue().version()).isEqualTo(1);
    }

    @Test
    void terminate_ByAnotherMember_IsRefused() {
        service.start(chatId, broadcastId, hostId, "host-session", state(trackId, 0.0, true));
        clearInvocations(notifier);

        assertThatThrownBy(() -> service.terminate(chatId, broadcastId, listenerId))
                .isInstanceOf(UserNotAuthorizedException.class);

        verify(notifier, never()).publish(any());
    }

    @Test
    void start_OfAnotherMembersBroadcast_IsRefused() {
        service.start(chatId, broadcastId, hostId, "host-session", state(trackId, 0.0, true));
        clearInvocations(notifier);

        assertThatThrownBy(() -> service.start(chatId, broadcastId, listenerId, "listener-session",
                state(trackId, 0.0, false)))
                .isInstanceOf(UserNotAuthorizedException.class);

        verify(notifier, never()).publish(any());
    }

    @Test
    void update_UnderAnotherChat_IsNotFound() {
        service.start(chatId, broadcastId, hostId, "host-session", state(trackId, 0.0, true));

        assertThatThrownBy(() -> service.update(UUID.randomUUID(), broadcastId, hostId, "host-session",
                state(trackId, 1.0, true)))
                .isInstanceOf(BroadcastNotFoundException.class);
    }

    @Test
    void update_UnknownBroadcast_IsNotFound() {
        assertThatThrownBy(() -> service.update(chatId, broadcastId, hostId, "host-session",
                state(trackId, 1.0, true)))
                .isInstanceOf(BroadcastNotFoundException.class);
    }

    @Test
    void update_TrackThatDoesNotExist_IsRefused() {
        service.start(chatId, broadcastId, hostId, "host-session", state(trackId, 0.0, true));
        UUID missing = UUID.randomUUID();
        when(trackLookup.exists(missing)).thenReturn(false);

        assertThatThrownBy(() -> service.update(chatId, broadcastId, hostId, "host-session",
                state(missing, 0.0, true)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_SameTrack_IsNotLookedUpAgain() {
        service.start(chatId, broadcastId, hostId, "host-session", state(trackId, 0.0, true));

        service.update(chatId, broadcastId, hostId, "host-session", state(trackId, 10.0, true));
        service.update(chatId, broadcastId, hostId, "host-session", state(trackId, 20.0, false));

        verify(trackLookup, times(1)).exists(trackId);
    }

    @Test
    void update_InvalidPlayback_IsRefused() {
        service.start(chatId, broadcastId, hostId, "host-session", state(trackId, 0.0, true));

        assertThatThrownBy(() -> service.update(chatId, broadcastId, hostId, "s", state(trackId, -1.0, true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(chatId, broadcastId, hostId, "s", state(trackId, Double.NaN, true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(chatId, broadcastId, hostId, "s", state(trackId, 1e9, true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(chatId, broadcastId, hostId, "s", state(null, 0.0, true)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void terminate_FreezesThePositionWhereItWas() {
        service.start(chatId, broadcastId, hostId, "host-session", state(trackId, 10.0, true));
        now += 4_000;

        service.terminate(chatId, broadcastId, hostId);

        verify(notifier, times(2)).publish(published.capture());
        BroadcastState ended = published.getValue();
        assertThat(ended.status()).isEqualTo(BroadcastStatus.TERMINATED);
        assertThat(ended.playing()).isFalse();
        assertThat(ended.position()).isEqualTo(14.0);
        assertThat(ended.version()).isEqualTo(2);
    }

    @Test
    void terminate_Twice_PublishesOnce() {
        service.start(chatId, broadcastId, hostId, "host-session", state(trackId, 0.0, true));
        service.terminate(chatId, broadcastId, hostId);
        clearInvocations(notifier);

        service.terminate(chatId, broadcastId, hostId);

        verify(notifier, never()).publish(any());
    }

    @Test
    void update_AfterTheEnd_IsRefused() {
        service.start(chatId, broadcastId, hostId, "host-session", state(trackId, 0.0, true));
        service.terminate(chatId, broadcastId, hostId);

        assertThatThrownBy(() -> service.update(chatId, broadcastId, hostId, "host-session",
                state(trackId, 1.0, true)))
                .isInstanceOf(BroadcastEndedException.class);
        assertThatThrownBy(() -> service.start(chatId, broadcastId, hostId, "host-session",
                state(trackId, 1.0, true)))
                .isInstanceOf(BroadcastEndedException.class);
    }

    @Test
    void start_NewBroadcast_EndsTheHostsPreviousOne() {
        UUID previous = UUID.randomUUID();
        service.start(chatId, previous, hostId, "host-session", state(trackId, 0.0, true));
        clearInvocations(notifier);

        service.start(chatId, broadcastId, hostId, "host-session", state(trackId, 0.0, true));

        verify(notifier, times(2)).publish(published.capture());
        List<BroadcastState> states = published.getAllValues();
        assertThat(states).anySatisfy(state -> {
            assertThat(state.broadcastId()).isEqualTo(previous);
            assertThat(state.status()).isEqualTo(BroadcastStatus.TERMINATED);
        });
        assertThat(states).anySatisfy(state -> {
            assertThat(state.broadcastId()).isEqualTo(broadcastId);
            assertThat(state.status()).isEqualTo(BroadcastStatus.ACTIVE);
        });
    }

    @Test
    void requestSync_SendsTheSnapshotToTheRequesterOnly() {
        service.start(chatId, broadcastId, hostId, "host-session", state(trackId, 42.0, false));
        clearInvocations(notifier);

        service.requestSync(chatId, broadcastId, listenerId);

        verify(notifier).sendSnapshot(eq(listenerId), eq(chatId), eq(broadcastId), published.capture());
        assertThat(published.getValue().position()).isEqualTo(42.0);
        verify(notifier, never()).publish(any());
    }

    @Test
    void requestSync_ForAnotherChatsBroadcast_AnswersAsUnknown() {
        service.start(chatId, broadcastId, hostId, "host-session", state(trackId, 0.0, true));
        UUID otherChat = UUID.randomUUID();

        service.requestSync(otherChat, broadcastId, listenerId);

        verify(notifier).sendSnapshot(eq(listenerId), eq(otherChat), eq(broadcastId), isNull());
    }

    @Test
    void endAwayHosts_HostDisconnectedLongerThanTheGrace_EndsTheBroadcast() {
        service.start(chatId, broadcastId, hostId, "host-session", state(trackId, 0.0, true));
        service.onDisconnect(disconnect("host-session"));

        now += BroadcastStateService.HOST_AWAY_GRACE.toMillis() - 1;
        assertThat(service.endAwayHosts()).isEmpty();

        now += 1;
        List<BroadcastState> ended = service.endAwayHosts();

        assertThat(ended).singleElement().satisfies(state -> {
            assertThat(state.broadcastId()).isEqualTo(broadcastId);
            assertThat(state.status()).isEqualTo(BroadcastStatus.TERMINATED);
        });
        verify(notifier, times(2)).publish(any());
    }

    @Test
    void start_FromTheHostsNewSession_ResumesTheBroadcast() {
        service.start(chatId, broadcastId, hostId, "old-session", state(trackId, 0.0, true));
        service.onDisconnect(disconnect("old-session"));
        now += 10_000;

        service.start(chatId, broadcastId, hostId, "new-session", state(trackId, 10.0, true));
        now += BroadcastStateService.HOST_AWAY_GRACE.toMillis();

        assertThat(service.endAwayHosts()).isEmpty();
        verify(notifier, times(2)).publish(published.capture());
        assertThat(published.getValue().version()).isEqualTo(2);
        assertThat(published.getValue().hostSessionId()).isEqualTo("new-session");
    }

    @Test
    void onDisconnect_OfAListenersSession_KeepsTheBroadcast() {
        service.start(chatId, broadcastId, hostId, "host-session", state(trackId, 0.0, true));
        service.onDisconnect(disconnect("listener-session"));
        now += BroadcastStateService.HOST_AWAY_GRACE.toMillis() * 2;

        assertThat(service.endAwayHosts()).isEmpty();
    }

    @Test
    void endHostedBy_Chat_EndsOnlyThatChatsBroadcasts() {
        UUID otherChat = UUID.randomUUID();
        UUID otherBroadcast = UUID.randomUUID();
        service.start(chatId, broadcastId, hostId, "host-session", state(trackId, 0.0, true));
        // A host runs one broadcast at a time, so the second one belongs to another host
        UUID otherHost = UUID.randomUUID();
        service.start(otherChat, otherBroadcast, otherHost, "other-session", state(trackId, 0.0, true));
        clearInvocations(notifier);

        service.endHostedBy(hostId, chatId);

        verify(notifier).publish(published.capture());
        assertThat(published.getValue().broadcastId()).isEqualTo(broadcastId);
    }

    @Test
    void endHostedBy_User_EndsAllTheirBroadcasts() {
        service.start(chatId, broadcastId, hostId, "host-session", state(trackId, 0.0, true));
        clearInvocations(notifier);

        service.endHostedBy(hostId);

        verify(notifier).publish(published.capture());
        assertThat(published.getValue().status()).isEqualTo(BroadcastStatus.TERMINATED);
    }

    @Test
    void forgetEnded_DropsBroadcastsEndedLongAgo() {
        service.start(chatId, broadcastId, hostId, "host-session", state(trackId, 0.0, true));
        service.terminate(chatId, broadcastId, hostId);
        now += BroadcastStateService.ENDED_KEPT_FOR.toMillis();

        service.forgetEnded();
        service.requestSync(chatId, broadcastId, listenerId);

        verify(notifier).sendSnapshot(eq(listenerId), eq(chatId), eq(broadcastId), isNull());
    }

    @Test
    void update_OvertakenByANewerOne_IsIgnored() {
        // The host pressed play and then pause; the server handled the pause first
        service.start(chatId, broadcastId, hostId, "host-session", sequenced(trackId, 0.0, false, 10L));
        service.update(chatId, broadcastId, hostId, "host-session", sequenced(trackId, 5.0, false, 12L));
        clearInvocations(notifier);

        service.update(chatId, broadcastId, hostId, "host-session", sequenced(trackId, 4.9, true, 11L));

        verify(notifier, never()).publish(any());
        service.requestSync(chatId, broadcastId, listenerId);
        verify(notifier).sendSnapshot(eq(listenerId), eq(chatId), eq(broadcastId), published.capture());
        assertThat(published.getValue().playing()).isFalse();
        assertThat(published.getValue().position()).isEqualTo(5.0);
        assertThat(published.getValue().version()).isEqualTo(2);
    }

    @Test
    void start_RepeatedWithAnOlderSequence_ChangesNothing() {
        service.start(chatId, broadcastId, hostId, "host-session", sequenced(trackId, 3.0, true, 20L));
        clearInvocations(notifier);

        service.start(chatId, broadcastId, hostId, "host-session", sequenced(trackId, 0.0, false, 19L));

        verify(notifier, never()).publish(any());
    }

    @Test
    void update_WithoutASequence_IsAlwaysApplied() {
        service.start(chatId, broadcastId, hostId, "host-session", sequenced(trackId, 0.0, false, 50L));

        service.update(chatId, broadcastId, hostId, "host-session", state(trackId, 1.0, true));

        verify(notifier, times(2)).publish(published.capture());
        assertThat(published.getValue().playing()).isTrue();
        assertThat(published.getValue().hostSequence()).isEqualTo(50L);
    }

    @Test
    void positionAt_AdvancesOnlyWhilePlaying() {
        BroadcastState playing = BroadcastState.builder().position(10).playing(true).anchorTime(1_000).build();
        BroadcastState paused = playing.toBuilder().playing(false).build();

        assertThat(BroadcastStateService.positionAt(playing, 3_500)).isEqualTo(12.5);
        assertThat(BroadcastStateService.positionAt(paused, 3_500)).isEqualTo(10);
        // A clock that is behind the anchor never moves the position back
        assertThat(BroadcastStateService.positionAt(playing, 500)).isEqualTo(10);
    }

    private static BroadcastSyncMessage state(UUID trackId, Double position, boolean playing) {
        return BroadcastSyncMessage.builder()
                .command(BroadcastCommand.UPDATE)
                .trackId(trackId)
                .position(position)
                .playing(playing)
                .build();
    }

    private static BroadcastSyncMessage sequenced(UUID trackId, Double position, boolean playing, Long sequence) {
        return BroadcastSyncMessage.builder()
                .command(BroadcastCommand.UPDATE)
                .trackId(trackId)
                .position(position)
                .playing(playing)
                .sequence(sequence)
                .build();
    }

    private static SessionDisconnectEvent disconnect(String sessionId) {
        return new SessionDisconnectEvent(new Object(), MessageBuilder.withPayload(new byte[0]).build(), sessionId,
                CloseStatus.NORMAL);
    }
}

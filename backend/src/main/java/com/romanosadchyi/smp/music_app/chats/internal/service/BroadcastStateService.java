package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.dto.BroadcastStatus;
import com.romanosadchyi.smp.music_app.chats.internal.dto.BroadcastSyncMessage;
import com.romanosadchyi.smp.music_app.chats.internal.exception.BroadcastEndedException;
import com.romanosadchyi.smp.music_app.chats.internal.exception.BroadcastNotFoundException;
import com.romanosadchyi.smp.music_app.music.external.TrackLookup;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The listening broadcasts of chats, kept on the server (KAN-109) with the anchor-time model of SyncChord rooms:
 * the host reports its state, the server stamps it with its own time and a new version and sends it to the topic,
 * and every listener computes the position from that anchor. Only the host may change a broadcast (KAN-110); any
 * member may ask for a snapshot, which only they receive. Membership of the chat is checked before this, by
 * {@link BroadcastStompGuard} on every SEND.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BroadcastStateService {

    /** How long a broadcast outlives the disconnect of its host's session, for a reconnect to resume it. */
    static final Duration HOST_AWAY_GRACE = Duration.ofSeconds(30);
    /** How long an ended broadcast is remembered, so a late listener learns that it ended. */
    static final Duration ENDED_KEPT_FOR = Duration.ofMinutes(10);
    /** Active broadcasts at most; a host has one at a time, so this bounds the number of hosts. */
    static final int MAX_ACTIVE_BROADCASTS = 10_000;
    /** Ended broadcasts kept per host, so starting many cannot fill the memory with ended ones. */
    static final int MAX_ENDED_PER_HOST = 3;
    static final double MAX_POSITION_SECONDS = 24 * 60 * 60;

    BroadcastNotifier notifier;
    TrackLookup trackLookup;
    Clock clock;
    Map<UUID, BroadcastState> broadcasts = new ConcurrentHashMap<>();
    /** {@link #MAX_ACTIVE_BROADCASTS}; a field so a test can lower it. */
    @NonFinal
    int maxActiveBroadcasts = MAX_ACTIVE_BROADCASTS;

    /**
     * Starts the broadcast with the sender as host; repeating it for the same broadcast applies the host's state.
     * Ends the sender's other broadcasts: a host plays one at a time.
     *
     * @throws UserNotAuthorizedException if another member hosts this broadcast
     * @throws BroadcastNotFoundException if the broadcast id belongs to another chat
     * @throws BroadcastEndedException    if the broadcast has ended
     * @throws IllegalArgumentException   if the playback state is invalid, names no existing track, or too many
     *                                    broadcasts are active
     */
    public void start(UUID chatId, UUID broadcastId, UUID userId, String sessionId, BroadcastSyncMessage message) {
        begin(chatId, broadcastId, userId, sessionId, message, true);
    }

    /**
     * Continues the host's broadcast from a new session after a reconnect.
     *
     * @throws BroadcastEndedException    if the broadcast has ended, or the server does not know it (it forgot an
     *                                    ended one, or restarted): it is not revived under an id the chat may
     *                                    already show as ended
     * @throws UserNotAuthorizedException if another member hosts this broadcast
     * @throws BroadcastNotFoundException if the broadcast id belongs to another chat
     * @throws IllegalArgumentException   if the playback state is invalid or names no existing track
     */
    public void resume(UUID chatId, UUID broadcastId, UUID userId, String sessionId, BroadcastSyncMessage message) {
        begin(chatId, broadcastId, userId, sessionId, message, false);
    }

    private void begin(UUID chatId, UUID broadcastId, UUID userId, String sessionId, BroadcastSyncMessage message,
                       boolean mayCreate) {
        Playback playback = playbackOf(message, broadcasts.get(broadcastId));
        // Approximate under concurrent starts; it bounds memory, it is not a quota
        if (mayCreate && !broadcasts.containsKey(broadcastId) && activeCount() >= maxActiveBroadcasts) {
            throw new IllegalArgumentException("Too many broadcasts are running; try again later");
        }
        boolean[] changed = {false};
        BroadcastState started = broadcasts.compute(broadcastId, (id, current) -> {
            long now = clock.millis();
            if (current == null) {
                if (!mayCreate) {
                    throw new BroadcastEndedException("This broadcast has ended");
                }
                changed[0] = true;
                return BroadcastState.builder()
                        .broadcastId(broadcastId)
                        .chatId(chatId)
                        .hostId(userId)
                        .hostSessionId(sessionId)
                        .hostSequence(playback.sequence() == null ? 0 : playback.sequence())
                        .status(BroadcastStatus.ACTIVE)
                        .trackId(playback.trackId())
                        .position(playback.position())
                        .playing(playback.playing())
                        .anchorTime(now)
                        .version(1)
                        .build();
            }
            requireActiveHost(current, chatId, userId);
            if (isStale(playback, current)) {
                return current;
            }
            changed[0] = true;
            return applied(current, sessionId, playback, now);
        });
        if (!changed[0]) {
            return;
        }
        log.info("User {} started or resumed broadcast {} in chat {}", userId, broadcastId, chatId);
        endWhere(state -> state.hostId().equals(userId) && !state.broadcastId().equals(broadcastId))
                .forEach(notifier::publish);
        forgetSurplusEnded(userId);
        notifier.publish(started);
    }

    private long activeCount() {
        return broadcasts.values().stream().filter(state -> state.status() == BroadcastStatus.ACTIVE).count();
    }

    /**
     * Keeps only the host's {@link #MAX_ENDED_PER_HOST} most recently ended broadcasts.
     */
    private void forgetSurplusEnded(UUID hostId) {
        broadcasts.values().stream()
                .filter(state -> state.hostId().equals(hostId) && state.status() == BroadcastStatus.TERMINATED)
                .sorted(Comparator.comparing(BroadcastState::endedAt).reversed())
                .skip(MAX_ENDED_PER_HOST)
                .toList()
                .forEach(state -> broadcasts.remove(state.broadcastId(), state));
    }

    /**
     * Applies the host's playback state.
     *
     * @throws BroadcastNotFoundException if there is no such broadcast in the chat
     * @throws UserNotAuthorizedException if the sender is not the host
     * @throws BroadcastEndedException    if the broadcast has ended
     * @throws IllegalArgumentException   if the playback state is invalid or names no existing track
     */
    public void update(UUID chatId, UUID broadcastId, UUID userId, String sessionId, BroadcastSyncMessage message) {
        Playback playback = playbackOf(message, broadcasts.get(broadcastId));
        boolean[] changed = {false};
        BroadcastState updated = broadcasts.compute(broadcastId, (id, current) -> {
            if (current == null) {
                throw new BroadcastNotFoundException("There is no such broadcast");
            }
            requireActiveHost(current, chatId, userId);
            if (isStale(playback, current)) {
                return current;
            }
            changed[0] = true;
            return applied(current, sessionId, playback, clock.millis());
        });
        if (changed[0]) {
            notifier.publish(updated);
        }
    }

    /**
     * Ends the broadcast; ending one that has already ended changes nothing.
     *
     * @throws BroadcastNotFoundException if there is no such broadcast in the chat
     * @throws UserNotAuthorizedException if the sender is not the host
     */
    public void terminate(UUID chatId, UUID broadcastId, UUID userId) {
        BroadcastState current = broadcasts.get(broadcastId);
        if (current == null || !current.chatId().equals(chatId)) {
            throw new BroadcastNotFoundException("There is no such broadcast");
        }
        requireHost(current, userId);
        List<BroadcastState> ended = endWhere(state -> state.broadcastId().equals(broadcastId));
        if (!ended.isEmpty()) {
            log.info("User {} ended broadcast {} in chat {}", userId, broadcastId, chatId);
        }
        ended.forEach(notifier::publish);
    }

    /**
     * Sends the current state to the member only. A broadcast of another chat, or one the server does not know, is
     * answered as ended, without telling which.
     */
    public void requestSync(UUID chatId, UUID broadcastId, UUID userId) {
        BroadcastState state = broadcasts.get(broadcastId);
        notifier.sendSnapshot(userId, chatId, broadcastId,
                state != null && state.chatId().equals(chatId) ? state : null);
    }

    /**
     * A disconnected host session starts the grace period of the broadcasts it hosts; a START or UPDATE from a new
     * session of the host ends it.
     */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        long now = clock.millis();
        broadcasts.replaceAll((id, state) -> state.status() == BroadcastStatus.ACTIVE
                && sessionId.equals(state.hostSessionId()) && state.hostAwaySince() == null
                ? state.toBuilder().hostAwaySince(now).build()
                : state);
    }

    /**
     * Ends the broadcasts whose host has been away longer than {@link #HOST_AWAY_GRACE} and tells their listeners.
     *
     * @return the ended broadcasts, for the caller to announce in their chats
     */
    List<BroadcastState> endAwayHosts() {
        long deadline = clock.millis() - HOST_AWAY_GRACE.toMillis();
        List<BroadcastState> ended = endWhere(state -> state.hostAwaySince() != null
                && state.hostAwaySince() <= deadline);
        ended.forEach(state -> {
            log.info("Ended broadcast {} in chat {}: its host is away", state.broadcastId(), state.chatId());
            notifier.publish(state);
        });
        return ended;
    }

    /**
     * Ends the user's broadcasts in a chat they may no longer read.
     */
    public void endHostedBy(UUID userId, UUID chatId) {
        endWhere(state -> state.hostId().equals(userId) && state.chatId().equals(chatId)).forEach(notifier::publish);
    }

    /**
     * Ends all of the user's broadcasts, when their account is deleted.
     */
    public void endHostedBy(UUID userId) {
        endWhere(state -> state.hostId().equals(userId)).forEach(notifier::publish);
    }

    /**
     * Forgets broadcasts that ended longer than {@link #ENDED_KEPT_FOR} ago.
     */
    public void forgetEnded() {
        long deadline = clock.millis() - ENDED_KEPT_FOR.toMillis();
        broadcasts.values().removeIf(state -> state.endedAt() != null && state.endedAt() <= deadline);
    }

    /**
     * Ends every active broadcast that matches, each atomically.
     *
     * @return the ended broadcasts, for the caller to publish
     */
    private List<BroadcastState> endWhere(Predicate<BroadcastState> matches) {
        List<BroadcastState> ended = new ArrayList<>();
        for (UUID broadcastId : List.copyOf(broadcasts.keySet())) {
            broadcasts.computeIfPresent(broadcastId, (id, state) -> {
                if (state.status() != BroadcastStatus.ACTIVE || !matches.test(state)) {
                    return state;
                }
                long now = clock.millis();
                BroadcastState terminated = state.toBuilder()
                        .status(BroadcastStatus.TERMINATED)
                        .position(positionAt(state, now))
                        .playing(false)
                        .anchorTime(now)
                        .version(state.version() + 1)
                        .hostAwaySince(null)
                        .endedAt(now)
                        .build();
                ended.add(terminated);
                return terminated;
            });
        }
        return ended;
    }

    /**
     * Whether the host sent this state before the one applied last; it overtook nothing and is ignored.
     */
    private static boolean isStale(Playback playback, BroadcastState current) {
        return playback.sequence() != null && playback.sequence() <= current.hostSequence();
    }

    private BroadcastState applied(BroadcastState current, String sessionId, Playback playback, long now) {
        return current.toBuilder()
                .hostSessionId(sessionId)
                .hostSequence(playback.sequence() == null ? current.hostSequence() : playback.sequence())
                .hostAwaySince(null)
                .trackId(playback.trackId())
                .position(playback.position())
                .playing(playback.playing())
                .anchorTime(now)
                .version(current.version() + 1)
                .build();
    }

    private static BroadcastState requireActiveHost(BroadcastState current, UUID chatId, UUID userId) {
        if (!current.chatId().equals(chatId)) {
            throw new BroadcastNotFoundException("There is no such broadcast");
        }
        requireHost(current, userId);
        if (current.status() != BroadcastStatus.ACTIVE) {
            throw new BroadcastEndedException("This broadcast has ended");
        }
        return current;
    }

    private static void requireHost(BroadcastState current, UUID userId) {
        if (!current.hostId().equals(userId)) {
            throw new UserNotAuthorizedException("Only the host may control this broadcast");
        }
    }

    /**
     * The validated playback state of a START or UPDATE. A track is checked only when it differs from the current
     * one, so the host's regular updates cost no lookup.
     */
    private Playback playbackOf(BroadcastSyncMessage message, BroadcastState current) {
        double position = message.position() == null ? 0 : message.position();
        if (!Double.isFinite(position) || position < 0 || position > MAX_POSITION_SECONDS) {
            throw new IllegalArgumentException("The position must be between 0 and 24 hours");
        }
        boolean playing = Boolean.TRUE.equals(message.playing());
        UUID trackId = message.trackId();
        if (trackId == null && playing) {
            throw new IllegalArgumentException("Nothing to play: pick a track first");
        }
        boolean newTrack = trackId != null && (current == null || !trackId.equals(current.trackId()));
        if (newTrack && !trackLookup.exists(trackId)) {
            throw new IllegalArgumentException("There is no such track");
        }
        return new Playback(trackId, position, playing, message.sequence());
    }

    /**
     * Where a broadcast is at a server time: its position advances with the clock while it plays.
     */
    static double positionAt(BroadcastState state, long serverTime) {
        if (!state.playing()) {
            return state.position();
        }
        return state.position() + Math.max(0, serverTime - state.anchorTime()) / 1000.0;
    }

    record Playback(UUID trackId, double position, boolean playing, Long sequence) {
    }
}

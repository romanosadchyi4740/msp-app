package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A host who stays offline for more than {@link #HOST_AWAY_LIMIT} hands the role to the participant who joined
 * earliest among those online, and playing playback pauses (KAN-36, FR-11), as {@link HostAwayScheduler} applies it.
 * It works from the stored offline time, so a restart does not reset it. A host who was never seen online keeps the
 * role: they may not have opened the room yet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class HostAwayService {

    static final Duration HOST_AWAY_LIMIT = Duration.ofSeconds(60);

    RoomRepository roomRepository;
    RoomParticipantRepository participantRepository;
    HostSuccession hostSuccession;
    Clock clock;

    /**
     * Open rooms whose host has been offline too long while someone else is online; {@link #handOver} then acts on
     * each under the room's lock.
     */
    @Transactional(readOnly = true)
    public List<UUID> findRoomsWithHostAway() {
        return participantRepository.findRoomsWithHostOfflineSince(cutoff(), RoomRole.HOST, RoomStatus.OPEN);
    }

    /**
     * Hands the room to the earliest-joined online participant if its host is still away after the room is locked;
     * otherwise, e.g. if the host came back meanwhile, changes nothing.
     */
    @Transactional
    public void handOver(UUID roomId) {
        Room room = roomRepository.findByIdForUpdate(roomId).orElse(null);
        if (room == null || room.getStatus() != RoomStatus.OPEN) {
            return;
        }
        RoomParticipant host = participantRepository.findByRoomIdAndRole(roomId, RoomRole.HOST).orElse(null);
        if (host == null || !isAway(host)) {
            return;
        }
        participantRepository.findFirstByRoomIdAndUserIdNotAndOnlineTrueOrderByJoinedAtAsc(roomId, host.getUserId())
                .ifPresent(successor -> {
                    hostSuccession.handOver(room, host, successor);
                    log.info("Host {} of room {} was away; the room passed to user {}", host.getUserId(), roomId,
                            successor.getUserId());
                });
    }

    private boolean isAway(RoomParticipant host) {
        return !host.isOnline() && host.getOfflineSince() != null && !host.getOfflineSince().isAfter(cutoff());
    }

    private Instant cutoff() {
        return clock.instant().minus(HOST_AWAY_LIMIT);
    }
}

package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEvent;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEventType;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.shared.util.AfterCommit;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * A participant leaves a room (KAN-36, FR-11). The room's row is locked first, like for lesson changes, so leaving
 * cannot interleave with them or with the away-host check.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomLeavingService {

    RoomAccess roomAccess;
    RoomParticipantRepository participantRepository;
    HostSuccession hostSuccession;
    ParticipantNotifier participantNotifier;
    RoomSubscriptionTracker roomSubscriptionTracker;

    /**
     * Frees the user's place. A leaving host of an open room hands the role on and playback pauses; the last one in
     * the room closes it, which ends its lesson. In a closed room nobody needs a host. After the commit the user's
     * subscriptions to the room's topics end.
     *
     * @throws RoomNotFoundException if there is no such room or the user is not in it
     */
    @Transactional
    public void leave(UUID userId, UUID roomId) {
        Room room = roomAccess.lock(roomId);
        RoomParticipant leaving = roomAccess.requireParticipant(userId, roomId);
        if (leaving.getRole() == RoomRole.HOST && room.getStatus() != RoomStatus.CLOSED) {
            hostSuccession.afterHostLeft(room, leaving);
        }
        participantRepository.delete(leaving);
        participantRepository.flush();
        participantNotifier.publish(roomId, new ParticipantEvent(ParticipantEventType.LEFT, userId,
                room.getHostId()));
        AfterCommit.run(() -> roomSubscriptionTracker.revoke(roomId, userId));
        log.info("User {} left room {}", userId, roomId);
    }
}

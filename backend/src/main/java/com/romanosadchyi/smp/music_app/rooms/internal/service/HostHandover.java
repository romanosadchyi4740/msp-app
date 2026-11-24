package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEvent;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEventType;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Moves the host role from one participant to another (FR-11) and tells the room. Who gets it, and what else
 * happens with the change, is decided by the callers ({@link HostTransferService}, {@link HostSuccession}). They hold
 * the room's lock and have checked who may make the change, so it runs only inside the caller's transaction.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Transactional(propagation = Propagation.MANDATORY)
public class HostHandover {

    RoomRepository roomRepository;
    RoomParticipantRepository participantRepository;
    ParticipantNotifier participantNotifier;
    Clock clock;

    /**
     * Makes {@code successor} the host and {@code host} a participant, and tells the room.
     */
    public void transfer(Room room, RoomParticipant host, RoomParticipant successor) {
        // One host per room is a unique index, so the old host steps down in the database first
        host.setRole(RoomRole.PARTICIPANT);
        participantRepository.saveAndFlush(host);
        successor.setRole(RoomRole.HOST);
        if (!successor.isOnline() && successor.getOfflineSince() == null) {
            // Handed the role while never seen online: away from now, so the role moves on if they do not come
            successor.setOfflineSince(clock.instant());
        }
        participantRepository.save(successor);
        room.setHostId(successor.getUserId());
        roomRepository.save(room);
        participantNotifier.publish(room.getId(), new ParticipantEvent(ParticipantEventType.HOST_CHANGED,
                successor.getUserId(), successor.getUserId()));
    }
}

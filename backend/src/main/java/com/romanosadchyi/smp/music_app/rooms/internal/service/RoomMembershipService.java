package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.external.RoomMembership;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class RoomMembershipService implements RoomMembership {

    RoomParticipantRepository participantRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean isParticipant(UUID roomId, UUID userId) {
        return participantRepository.findByRoomIdAndUserId(roomId, userId).isPresent();
    }
}

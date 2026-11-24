package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.shared.security.StompDestinationGuard;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A room's STOMP destinations ({@code /topic/rooms/{roomId}/...} and {@code /app/rooms/{roomId}/...}) are open only to
 * its current participants (KAN-23); someone who left the room, or was never in it, is refused.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class RoomStompGuard implements StompDestinationGuard {

    static final Pattern ROOM_DESTINATION = Pattern.compile("^/(?:topic|app)/rooms(?:/([^/]*))?(?:/.*)?$");

    RoomParticipantRepository participantRepository;

    @Override
    public boolean guards(String destination) {
        return ROOM_DESTINATION.matcher(destination).matches();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean mayAccess(UUID userId, String destination) {
        return roomIdOf(destination)
                .map(roomId -> participantRepository.findByRoomIdAndUserId(roomId, userId).isPresent())
                .orElse(false);
    }

    /**
     * The room id of the destination, only in the canonical lower-case form the server sends to, so one room has
     * exactly one set of destinations.
     */
    static Optional<UUID> roomIdOf(String destination) {
        Matcher matcher = ROOM_DESTINATION.matcher(destination);
        if (!matcher.matches() || matcher.group(1) == null) {
            return Optional.empty();
        }
        try {
            UUID roomId = UUID.fromString(matcher.group(1));
            return roomId.toString().equals(matcher.group(1)) ? Optional.of(roomId) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

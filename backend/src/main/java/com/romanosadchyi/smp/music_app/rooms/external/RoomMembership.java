package com.romanosadchyi.smp.music_app.rooms.external;

import java.util.UUID;

/**
 * Who is in a room right now, for modules that relay between its participants, such as the WebRTC signaling in
 * {@code rtc}.
 */
public interface RoomMembership {

    /**
     * Whether the user is a current participant of the room; false if there is no such room.
     */
    boolean isParticipant(UUID roomId, UUID userId);
}

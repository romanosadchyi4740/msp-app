package com.romanosadchyi.smp.music_app.rooms.external;

import java.util.UUID;

/**
 * Who is in a room right now, for modules that relay between its participants, such as the WebRTC signaling in
 * {@code rtc}.
 */
public interface RoomMembership {

    /**
     * Whether the user is a current participant of the room and the room is open; false if there is no such room. A
     * closed room is over, so nothing is relayed in it any more.
     */
    boolean isParticipantOfOpenRoom(UUID roomId, UUID userId);
}

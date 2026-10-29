package com.romanosadchyi.smp.music_app.rooms.internal.repository;

import java.util.UUID;

/**
 * The number of participants of one room.
 */
public interface RoomParticipantCount {
    UUID getRoomId();

    long getCount();
}

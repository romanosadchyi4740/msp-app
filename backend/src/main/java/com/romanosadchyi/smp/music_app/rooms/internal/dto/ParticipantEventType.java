package com.romanosadchyi.smp.music_app.rooms.internal.dto;

public enum ParticipantEventType {
    JOINED,
    LEFT,
    /**
     * The participant opened the room: subscribed to its participants topic in a first session.
     */
    ONLINE,
    /**
     * The participant's last session in the room unsubscribed or disconnected.
     */
    OFFLINE,
    HOST_CHANGED,
    /**
     * The participant reported a new round-trip time to the others (KAN-48).
     */
    RTT
}

package com.romanosadchyi.smp.music_app.chats.internal.dto;

public enum BroadcastStatus {
    ACTIVE,
    /** Ended by the host, or because the host left: by disconnecting, leaving the chat or deleting the account. */
    TERMINATED
}

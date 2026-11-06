package com.romanosadchyi.smp.music_app.rooms.internal.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Marks everyone offline once all beans exist, which is before the web server starts and before any client can
 * reconnect and come online again; after start-up, a reset would wipe those fresh connections.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class RoomPresenceReset implements SmartInitializingSingleton {

    RoomPresenceService roomPresenceService;

    @Override
    public void afterSingletonsInstantiated() {
        roomPresenceService.markAllOffline();
    }
}

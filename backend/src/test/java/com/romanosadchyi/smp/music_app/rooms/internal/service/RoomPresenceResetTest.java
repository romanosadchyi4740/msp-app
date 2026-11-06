package com.romanosadchyi.smp.music_app.rooms.internal.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RoomPresenceResetTest {

    @Mock
    RoomPresenceService roomPresenceService;

    @InjectMocks
    RoomPresenceReset roomPresenceReset;

    @Test
    void afterSingletonsInstantiated_MarksEveryoneOffline() {
        roomPresenceReset.afterSingletonsInstantiated();

        verify(roomPresenceService).markAllOffline();
    }
}

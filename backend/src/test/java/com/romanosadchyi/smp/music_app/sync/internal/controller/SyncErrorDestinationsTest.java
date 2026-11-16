package com.romanosadchyi.smp.music_app.sync.internal.controller;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.annotation.SendToUser;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where the sync channel's errors go (KAN-96): a refused command to its room's own queue, a failed snapshot to the
 * shared one, each to the failing session only. The command side is also covered over STOMP by
 * {@code RoomSyncStompIntegrationTest}; a failed snapshot cannot be caused reliably there, since the guard refuses
 * non-participants before the handler runs.
 */
class SyncErrorDestinationsTest {

    @Test
    void commandErrors_GoToTheRoomsCommandErrorQueueOfTheSendingSession() throws Exception {
        SendToUser sendToUser = RoomSyncController.class.getMethod("handleError", Exception.class)
                .getAnnotation(SendToUser.class);

        assertThat(sendToUser.destinations()).containsExactly("/queue/rooms/{roomId}/command-errors");
        assertThat(sendToUser.broadcast()).isFalse();
    }

    @Test
    void snapshotErrors_GoToTheSharedErrorQueueOfTheSubscribingSession() throws Exception {
        SendToUser sendToUser = RoomSnapshotController.class.getMethod("handleError", Exception.class)
                .getAnnotation(SendToUser.class);

        assertThat(sendToUser.destinations()).containsExactly("/queue/errors");
        assertThat(sendToUser.broadcast()).isFalse();
    }
}

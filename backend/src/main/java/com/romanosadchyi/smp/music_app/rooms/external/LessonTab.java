package com.romanosadchyi.smp.music_app.rooms.external;

import java.util.UUID;

/**
 * A lesson and the tab it shows.
 *
 * @param tempoBpm      the tab's tempo, or a default if the tab has none
 * @param timeSignature the tab's time signature such as {@code 6/8}; null if the tab has none
 */
public record LessonTab(UUID sessionId, UUID roomId, UUID tabId, int tempoBpm, String timeSignature) {
}

package com.romanosadchyi.smp.music_app.rooms.internal.dto;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonEndReason;

import java.time.Instant;
import java.util.UUID;

/**
 * A change of a room's lesson, sent to {@code /topic/rooms/{roomId}/state}; {@code type} tells it apart from the
 * playback state sent to the same topic.
 *
 * @param endsAt when the limit ends the lesson; null without a limit
 * @param reason set only for {@link LessonEventType#LESSON_ENDED}
 */
public record LessonEvent(LessonEventType type, UUID sessionId, UUID tabId, Instant endsAt, LessonEndReason reason) {
}

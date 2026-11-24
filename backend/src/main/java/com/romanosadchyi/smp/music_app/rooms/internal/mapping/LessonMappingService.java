package com.romanosadchyi.smp.music_app.rooms.internal.mapping;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.LessonEndReason;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.LessonResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonSession;
import com.romanosadchyi.smp.music_app.rooms.internal.service.LessonSessions;
import org.springframework.stereotype.Component;

/**
 * Maps lessons to the API models generated from {@code openapi/rooms.yaml}.
 */
@Component
public class LessonMappingService {

    public LessonResponse map(LessonSession session) {
        return new LessonResponse()
                .id(session.getId())
                .roomId(session.getRoom().getId())
                .tabId(session.getTabId())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .limitMinutes(session.getLimitMinutes())
                .endsAt(LessonSessions.endsAt(session))
                .endReason(session.getEndReason() != null
                        ? LessonEndReason.valueOf(session.getEndReason().name())
                        : null);
    }
}

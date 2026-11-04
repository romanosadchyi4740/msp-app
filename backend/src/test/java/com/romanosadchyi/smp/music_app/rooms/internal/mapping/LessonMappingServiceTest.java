package com.romanosadchyi.smp.music_app.rooms.internal.mapping;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.LessonResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonEndReason;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonSession;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LessonMappingServiceTest {

    LessonMappingService lessonMappingService = new LessonMappingService();

    Instant started = Instant.parse("2026-10-29T10:00:00Z");
    Room room = Room.builder().id(UUID.randomUUID()).build();

    @Test
    void map_ActiveFreeLesson_HasItsEndTime() {
        LessonSession session = LessonSession.builder()
                .id(UUID.randomUUID())
                .room(room)
                .tabId(UUID.randomUUID())
                .startedAt(started)
                .limitMinutes(40)
                .build();

        LessonResponse response = lessonMappingService.map(session);

        assertThat(response.getId()).isEqualTo(session.getId());
        assertThat(response.getRoomId()).isEqualTo(room.getId());
        assertThat(response.getTabId()).isEqualTo(session.getTabId());
        assertThat(response.getStartedAt()).isEqualTo(started);
        assertThat(response.getLimitMinutes()).isEqualTo(40);
        assertThat(response.getEndsAt()).isEqualTo(Instant.parse("2026-10-29T10:40:00Z"));
        assertThat(response.getEndedAt()).isNull();
        assertThat(response.getEndReason()).isNull();
    }

    @Test
    void map_EndedPremiumLesson_HasNoLimitAndItsReason() {
        Instant ended = started.plusSeconds(7200);
        LessonSession session = LessonSession.builder()
                .id(UUID.randomUUID())
                .room(room)
                .startedAt(started)
                .endedAt(ended)
                .endReason(LessonEndReason.ROOM_CLOSED)
                .build();

        LessonResponse response = lessonMappingService.map(session);

        assertThat(response.getTabId()).isNull();
        assertThat(response.getLimitMinutes()).isNull();
        assertThat(response.getEndsAt()).isNull();
        assertThat(response.getEndedAt()).isEqualTo(ended);
        assertThat(response.getEndReason()).hasToString("ROOM_CLOSED");
    }
}

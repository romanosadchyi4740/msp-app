package com.romanosadchyi.smp.music_app.rooms.internal.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

/**
 * A lesson in a room (lab 7: Session). A room has at most one active lesson, the one without {@code endedAt}.
 */
@Entity
@Table(name = "lesson_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LessonSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    Room room;

    /**
     * Null once the tab is deleted.
     */
    @Column(name = "tab_id")
    UUID tabId;

    @Column(name = "started_at", nullable = false, updatable = false)
    Instant startedAt;

    @Column(name = "ended_at")
    Instant endedAt;

    /**
     * The lesson's length limit; null means no limit (Premium).
     */
    @Column(name = "limit_minutes")
    Integer limitMinutes;

    @Column(name = "limit_warning_sent", nullable = false)
    @Builder.Default
    boolean limitWarningSent = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 20)
    LessonEndReason endReason;
}

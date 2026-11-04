package com.romanosadchyi.smp.music_app.sync.internal.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

/**
 * The shared position of one lesson in the anchor-time model: at server time {@code anchorTime} the lesson is at
 * ({@code measure}, {@code beat}); while {@code PLAYING}, clients add the beats elapsed since at {@code tempoBpm}.
 * Every change increments {@code version}, and clients drop states with a version they already applied.
 */
@Entity
@Table(name = "playback_states")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlaybackState {

    /**
     * The lesson's id in {@code rooms}.
     */
    @Id
    @Column(name = "session_id", updatable = false, nullable = false)
    UUID sessionId;

    /**
     * 1-based.
     */
    @Column(nullable = false)
    int measure;

    /**
     * Beats into the measure, 0-based; fractional while paused between beats.
     */
    @Column(nullable = false)
    double beat;

    @Column(name = "tempo_bpm", nullable = false)
    int tempoBpm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    PlaybackStatus status;

    /**
     * Server epoch milliseconds.
     */
    @Column(name = "anchor_time", nullable = false)
    long anchorTime;

    @Column(name = "metronome_on", nullable = false)
    boolean metronomeOn;

    @Version
    @Column(nullable = false)
    Long version;
}

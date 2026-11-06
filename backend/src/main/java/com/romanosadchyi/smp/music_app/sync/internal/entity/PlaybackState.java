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
     * Quarter-note beats into the measure, 0-based; fractional while paused between beats and in signatures such
     * as 6/8, whose measure holds 3 quarter beats.
     */
    @Column(nullable = false)
    double beat;

    /**
     * Quarter notes per minute, as Guitar Pro files and alphaTab count the tempo.
     */
    @Column(name = "tempo_bpm", nullable = false)
    int tempoBpm;

    /**
     * The upper number of the time signature.
     */
    @Column(name = "beats_per_measure", nullable = false)
    @Builder.Default
    int beatsPerMeasure = 4;

    /**
     * The lower number of the time signature, the note value of one beat.
     */
    @Column(name = "beat_unit", nullable = false)
    @Builder.Default
    int beatUnit = 4;

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

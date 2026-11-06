package com.romanosadchyi.smp.music_app.sync.internal.dto;

import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackStatus;

import java.util.UUID;

/**
 * A lesson's playback as clients see it. While {@code PLAYING}, a client adds
 * {@code (serverNow - anchorTime) * tempoBpm / 60000} quarter beats to ({@code measure}, {@code beat}); a measure
 * holds {@code beatsPerMeasure * 4 / beatUnit} quarter beats. Clients drop a state whose {@code version} they have
 * already applied.
 *
 * @param anchorTime server epoch milliseconds; in the future during the count-in
 * @param serverTime the server clock when the snapshot was taken
 */
public record PlaybackStateDto(
        UUID sessionId,
        int measure,
        double beat,
        int tempoBpm,
        int beatsPerMeasure,
        int beatUnit,
        PlaybackStatus status,
        long anchorTime,
        boolean metronomeOn,
        long version,
        long serverTime) {
}

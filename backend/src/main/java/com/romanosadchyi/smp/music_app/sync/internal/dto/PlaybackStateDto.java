package com.romanosadchyi.smp.music_app.sync.internal.dto;

import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackStatus;

import java.util.UUID;

/**
 * A lesson's playback as clients see it. While {@code PLAYING}, a client adds
 * {@code (serverNow - anchorTime) * tempoBpm / 60000} quarter beats to ({@code measure}, {@code beat}) and wraps the
 * result into measures with the lengths of the tab it renders, as a Guitar Pro tab can change time signature
 * (KAN-95); without them, every measure holds {@code beatsPerMeasure * 4 / beatUnit} quarter beats. Clients drop a
 * state whose {@code version} they have already applied.
 *
 * @param beat            quarter beats since the start of {@code measure}; may run past its end
 * @param beatsPerMeasure the tab's time signature, which also sets the length of the count-in
 * @param anchorTime      server epoch milliseconds; in the future during the count-in
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

package com.romanosadchyi.smp.music_app.tabs.internal.parsing;

import java.util.List;

/**
 * The beats between two bar lines; no beats is a measure of rest.
 *
 * @param repeatOpen  the measure starts a repeat ({@code ||:})
 * @param repeatCount how often the repeat ending with this measure is played ({@code :||}, {@code x3}); 0 if none
 */
public record AsciiMeasure(List<AsciiBeat> beats, boolean repeatOpen, int repeatCount) {

    public AsciiMeasure {
        beats = List.copyOf(beats);
    }
}

package com.romanosadchyi.smp.music_app.sync.internal.service;

/**
 * A place in the tab.
 *
 * @param measure 1-based
 * @param beat    quarter beats into the measure, 0-based
 */
public record Position(int measure, double beat) {
}

package com.romanosadchyi.smp.music_app.sync.internal.service;

/**
 * A place in the tab.
 *
 * @param measure 1-based
 * @param beat    quarter beats since the start of the measure, 0-based; may run past its end, as only the clients know
 *                how long each measure of the tab is (KAN-95)
 */
public record Position(int measure, double beat) {
}

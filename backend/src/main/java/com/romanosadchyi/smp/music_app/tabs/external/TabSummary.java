package com.romanosadchyi.smp.music_app.tabs.external;

import java.util.UUID;

/**
 * The tab fields other modules need, e.g. to start a lesson at the tab's tempo.
 *
 * @param tempoBpm      null if the tab has none
 * @param timeSignature such as {@code 4/4}; null if the tab has none
 */
public record TabSummary(UUID id, String title, Integer tempoBpm, String timeSignature) {
}

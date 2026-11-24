package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.LessonTabRequest;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.LessonTabNotFoundException;
import com.romanosadchyi.smp.music_app.tabs.external.TabLookup;
import com.romanosadchyi.smp.music_app.tabs.external.TabSummary;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The tab a host chose for a lesson.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LessonTabResolver {

    /**
     * The tempo of a lesson whose tab has none.
     */
    static final int DEFAULT_TEMPO_BPM = 120;

    TabLookup tabLookup;

    /**
     * @throws IllegalArgumentException if the request names no tab
     */
    public UUID requireTabId(LessonTabRequest request) {
        if (request == null || request.getTabId() == null) {
            throw new IllegalArgumentException("A tab is required");
        }
        return request.getTabId();
    }

    /**
     * @throws LessonTabNotFoundException if there is no such tab or the host may not read it
     */
    public TabSummary readableTab(UUID tabId, UUID hostId) {
        return tabLookup.findReadable(tabId, hostId)
                .orElseThrow(() -> new LessonTabNotFoundException("Tab not found"));
    }

    public static int tempoOf(TabSummary tab) {
        return tab.tempoBpm() != null ? tab.tempoBpm() : DEFAULT_TEMPO_BPM;
    }
}

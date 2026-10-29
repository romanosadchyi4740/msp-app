package com.romanosadchyi.smp.music_app.tabs.external;

import java.util.Optional;
import java.util.UUID;

public interface TabLookup {

    /**
     * The tab, if the user may read it: their own, or a public one that is not hidden. Empty otherwise, also when
     * there is no such tab.
     */
    Optional<TabSummary> findReadable(UUID tabId, UUID userId);
}

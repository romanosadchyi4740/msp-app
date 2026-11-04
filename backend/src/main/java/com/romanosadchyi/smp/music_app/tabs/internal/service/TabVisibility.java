package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.tabs.internal.entity.Tab;

import java.util.UUID;

/**
 * Who may read a tab: its owner, and others only while it is public and not hidden.
 */
final class TabVisibility {

    private TabVisibility() {
    }

    static boolean isReadableBy(Tab tab, UUID userId) {
        return tab.getOwnerId().equals(userId) || (tab.isPublic() && !tab.isHidden());
    }
}

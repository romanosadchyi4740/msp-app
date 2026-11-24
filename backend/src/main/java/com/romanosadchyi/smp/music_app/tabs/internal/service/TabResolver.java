package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.tabs.external.TabReadGrant;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.Tab;
import com.romanosadchyi.smp.music_app.tabs.internal.exception.TabNotFoundException;
import com.romanosadchyi.smp.music_app.tabs.internal.repository.TabRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Finds the tab a request is about: one the user owns, for changes, or one the user may read. A tab is readable by its
 * owner, and by others only while it is public and not hidden, or while a {@link TabReadGrant} lets them read it (the
 * tab of an active lesson in a room they are in, KAN-94); to everyone else it answers as not found, so private tabs do
 * not reveal that they exist.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class TabResolver {

    TabRepository tabRepository;
    List<TabReadGrant> tabReadGrants;

    /**
     * @throws TabNotFoundException       if there is no such tab
     * @throws UserNotAuthorizedException if the tab is someone else's
     */
    Tab getOwnTab(UUID userId, UUID tabId) {
        Tab tab = tabRepository.findById(tabId)
                .orElseThrow(() -> new TabNotFoundException("Tab not found"));
        if (!tab.getOwnerId().equals(userId)) {
            throw new UserNotAuthorizedException("You can only change your own tabs");
        }
        return tab;
    }

    /**
     * @throws TabNotFoundException if there is no such tab or the user may not read it
     */
    Tab getReadableTab(UUID userId, UUID tabId) {
        return tabRepository.findById(tabId)
                .filter(tab -> TabVisibility.isReadableBy(tab, userId) || isGranted(tab, userId))
                .orElseThrow(() -> new TabNotFoundException("Tab not found"));
    }

    /**
     * Whether another module lets the user read the tab; never for a hidden one.
     */
    private boolean isGranted(Tab tab, UUID userId) {
        return !tab.isHidden() && tabReadGrants.stream().anyMatch(grant -> grant.allowsRead(tab.getId(), userId));
    }
}

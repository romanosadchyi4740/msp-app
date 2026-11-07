package com.romanosadchyi.smp.music_app.tabs.external;

import java.util.UUID;

/**
 * Lets another module open a tab to a user beyond its owner and the public (KAN-94): {@code rooms} lets the
 * participants of a room read the tab of its active lesson, which may be the host's private tab. Implemented by the
 * modules that grant access, so {@code tabs} does not depend on them. A grant never opens a hidden tab, and only
 * reading the tab and its file is affected: what a host may start a lesson with is still decided by
 * {@link TabLookup}.
 */
public interface TabReadGrant {

    /**
     * Whether the user may read the tab although it is neither theirs nor public.
     */
    boolean allowsRead(UUID tabId, UUID userId);
}

package com.romanosadchyi.smp.music_app.shared.security;

import java.util.UUID;

/**
 * Decides who may subscribe or send to a family of STOMP destinations, e.g. a room's topics. A module that owns
 * destinations implements it, so {@code shared} checks them without depending on the module (KAN-23).
 */
public interface StompDestinationGuard {

    /**
     * Whether this guard decides about the destination; a destination no guard claims is open to every
     * authenticated user, as user queues ({@code /user/queue/...}) are.
     */
    boolean guards(String destination);

    /**
     * Whether the user may subscribe or send to a destination this guard claims. A destination it claims but cannot
     * read, such as one with a malformed id, is denied.
     */
    boolean mayAccess(UUID userId, String destination);
}

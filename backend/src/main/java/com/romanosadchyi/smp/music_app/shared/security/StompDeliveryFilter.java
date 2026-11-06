package com.romanosadchyi.smp.music_app.shared.security;

/**
 * Decides whether a message the broker sends to a subscription still reaches its session. A module implements it
 * for its own destinations, e.g. to stop delivery to someone who left a room after subscribing, which a check on
 * {@code SUBSCRIBE} alone cannot catch.
 */
public interface StompDeliveryFilter {

    boolean mayDeliver(String sessionId, String destination);
}

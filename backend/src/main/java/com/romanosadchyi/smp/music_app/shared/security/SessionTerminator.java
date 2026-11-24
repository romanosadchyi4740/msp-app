package com.romanosadchyi.smp.music_app.shared.security;

import java.util.UUID;

/**
 * Ends a user's open real-time sessions, e.g. when their account is deleted. Best effort: it never throws, so a
 * caller that runs after a commit is not turned into an error.
 */
public interface SessionTerminator {

    void closeSessionsOf(UUID userId);
}

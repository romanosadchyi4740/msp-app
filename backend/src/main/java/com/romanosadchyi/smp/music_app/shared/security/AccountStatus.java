package com.romanosadchyi.smp.music_app.shared.security;

import java.util.UUID;

/**
 * Whether a token's subject may still act. Tokens are stateless, so a deleted account's token would stay valid until
 * it expires; the JWT filter and the STOMP interceptor ask this on every request. Implemented by the users module,
 * which {@code shared} cannot depend on.
 */
public interface AccountStatus {

    /**
     * False for an unknown or deleted account.
     */
    boolean isActive(UUID userId);
}

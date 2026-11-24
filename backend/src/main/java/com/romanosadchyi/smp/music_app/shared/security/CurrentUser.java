package com.romanosadchyi.smp.music_app.shared.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The user of the current request, for controllers that implement a generated OpenAPI interface and so cannot take
 * an {@code @AuthenticationPrincipal} parameter. Hand-written controllers keep using the parameter.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * @throws AuthenticationCredentialsNotFoundException if the request is not authenticated with a JWT. That only
     *         happens if the security chain lets an unauthenticated request reach a protected controller; thrown
     *         there, it ends in the generic 500 handler, not a 401.
     */
    public static AuthenticatedUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new AuthenticationCredentialsNotFoundException("No authenticated user in the security context");
    }
}

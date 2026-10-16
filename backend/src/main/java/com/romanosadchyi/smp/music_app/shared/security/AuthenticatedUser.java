package com.romanosadchyi.smp.music_app.shared.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * The current user, built from the JWT claims without loading the {@code User} entity.
 * {@link #getName()} is the user id, so {@code convertAndSendToUser(userId.toString(), ...)} reaches the user's
 * STOMP sessions.
 *
 * @param role the authority name, e.g. {@code ROLE_ARTIST}
 */
public record AuthenticatedUser(UUID id, String email, String role) implements Principal {

    @Override
    public String getName() {
        return id.toString();
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }

}

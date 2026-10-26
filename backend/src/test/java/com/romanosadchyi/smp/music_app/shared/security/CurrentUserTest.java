package com.romanosadchyi.smp.music_app.shared.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void get_AuthenticatedWithAJwt_ReturnsThePrincipal() {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "user@example.com", "ROLE_USER");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.authorities()));

        assertThat(CurrentUser.get()).isSameAs(user);
    }

    @Test
    void get_NoAuthentication_Throws() {
        assertThatThrownBy(CurrentUser::get).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void get_AnonymousAuthentication_Throws() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThatThrownBy(CurrentUser::get).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}

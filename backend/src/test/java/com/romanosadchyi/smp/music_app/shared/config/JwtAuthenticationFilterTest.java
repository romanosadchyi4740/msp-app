package com.romanosadchyi.smp.music_app.shared.config;

import com.romanosadchyi.smp.music_app.shared.security.AccountStatus;
import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import com.romanosadchyi.smp.music_app.shared.security.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    JwtService jwtService;

    @Mock
    AccountStatus accountStatus;

    @InjectMocks
    JwtAuthenticationFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validToken_SetsAuthenticatedUserPrincipal() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "a@b.c", "ROLE_ARTIST");
        when(jwtService.parseToken("good")).thenReturn(user);
        when(accountStatus.isActive(user.id())).thenReturn(true);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("Bearer good"), new MockHttpServletResponse(), chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isEqualTo(user);
        assertThat(authentication.getName()).isEqualTo(user.id().toString());
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_ARTIST");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void tokenOfADeletedAccount_LeavesRequestUnauthenticated() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "deleted@system.local", "ROLE_USER");
        when(jwtService.parseToken("old")).thenReturn(user);
        when(accountStatus.isActive(user.id())).thenReturn(false);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("Bearer old"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void invalidToken_LeavesRequestUnauthenticated() throws Exception {
        when(jwtService.parseToken("expired")).thenThrow(new ExpiredJwtException(null, null, "expired"));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("Bearer expired"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void noBearerHeader_SkipsTokenParsing() throws Exception {
        filter.doFilter(request(null), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
    }

    private static MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }
}

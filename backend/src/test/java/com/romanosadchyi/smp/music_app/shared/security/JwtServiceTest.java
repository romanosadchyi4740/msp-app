package com.romanosadchyi.smp.music_app.shared.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    static final String SECRET = "7d2f1c9a4e8b3f6a0c5d2e9b8a7f4c1e3b6d9a2f5c8e1b4d7a0f3c6e9b2d5a8f";
    static final String OTHER_SECRET = "0a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f9";

    JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = jwtService(SECRET, 60_000);
    }

    @Test
    void parseToken_GeneratedToken_ReturnsSameUser() {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "artist@example.com", "ROLE_ARTIST");

        AuthenticatedUser parsed = jwtService.parseToken(jwtService.generateToken(user));

        assertThat(parsed).isEqualTo(user);
        assertThat(parsed.getName()).isEqualTo(user.id().toString());
        assertThat(parsed.authorities()).extracting("authority").containsExactly("ROLE_ARTIST");
    }

    @Test
    void parseToken_ExpiredToken_Throws() {
        String token = jwtService(SECRET, -1_000)
                .generateToken(new AuthenticatedUser(UUID.randomUUID(), "a@b.c", "ROLE_USER"));

        assertThatThrownBy(() -> jwtService.parseToken(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parseToken_SignedWithOtherKey_Throws() {
        String token = jwtService(OTHER_SECRET, 60_000)
                .generateToken(new AuthenticatedUser(UUID.randomUUID(), "a@b.c", "ROLE_USER"));

        assertThatThrownBy(() -> jwtService.parseToken(token)).isInstanceOf(SignatureException.class);
    }

    @Test
    void parseToken_WithoutRoleClaim_Throws() {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.parseToken(token)).isInstanceOf(IllegalArgumentException.class);
    }

    private static JwtService jwtService(String secret, long expiration) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", secret);
        ReflectionTestUtils.setField(service, "jwtExpiration", expiration);
        return service;
    }
}

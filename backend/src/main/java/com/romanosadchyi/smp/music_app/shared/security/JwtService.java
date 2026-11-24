package com.romanosadchyi.smp.music_app.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JwtService {

    static final String EMAIL_CLAIM = "email";
    static final String ROLE_CLAIM = "role";

    SecretKey signInKey;
    long jwtExpiration;

    /**
     * @throws IllegalArgumentException if the secret is not Base64 of at least 256 bits, so startup fails
     */
    public JwtService(JwtProperties properties) {
        try {
            signInKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        } catch (DecodingException | WeakKeyException ex) {
            throw new IllegalArgumentException("jwt.secret must be Base64 of at least 32 bytes", ex);
        }
        jwtExpiration = properties.expiration();
    }

    public String generateToken(AuthenticatedUser user) {
        return Jwts.builder()
                .subject(user.id().toString())
                .claim(ROLE_CLAIM, user.role())
                .claim(EMAIL_CLAIM, user.email())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(signInKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Reads the user from a token's claims.
     *
     * @throws JwtException             if the signature is invalid or the token has expired
     * @throws IllegalArgumentException if the subject is not a UUID or the role claim is missing
     */
    public AuthenticatedUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signInKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String role = claims.get(ROLE_CLAIM, String.class);
        if (role == null) {
            throw new IllegalArgumentException("Token has no role claim");
        }
        return new AuthenticatedUser(UUID.fromString(claims.getSubject()), claims.get(EMAIL_CLAIM, String.class), role);
    }
}

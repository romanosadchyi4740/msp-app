package com.romanosadchyi.smp.music_app.users.internal.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import com.romanosadchyi.smp.music_app.shared.security.JwtService;
import com.romanosadchyi.smp.music_app.users.external.UserRole;
import com.romanosadchyi.smp.music_app.users.internal.dto.AuthResponseDto;
import com.romanosadchyi.smp.music_app.users.internal.dto.LoginRequestDto;
import com.romanosadchyi.smp.music_app.users.internal.dto.RegisterRequestDto;
import com.romanosadchyi.smp.music_app.users.internal.entity.AuthProvider;
import com.romanosadchyi.smp.music_app.users.internal.entity.User;
import com.romanosadchyi.smp.music_app.users.internal.exception.EmailAlreadyInUseException;
import com.romanosadchyi.smp.music_app.users.internal.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthService {

    static final String EMAIL_IN_USE = "Email already in use";
    static final String EMAIL_UNIQUE_CONSTRAINT = "email_unique";
    static final String GOOGLE_FAILED = "Failed to authenticate with Google";

    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    JwtService jwtService;

    @NonFinal
    @Value("${google.client.id}")
    String googleClientId;

    /**
     * @throws EmailAlreadyInUseException if an account has the email, also when another registration with it
     *                                    commits between the check and the insert
     */
    @Transactional
    public AuthResponseDto register(RegisterRequestDto request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyInUseException(EMAIL_IN_USE);
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .role(UserRole.ROLE_USER)
                .provider(AuthProvider.LOCAL)
                .avatarUrl("https://ui-avatars.com/api/?name=" + request.displayName())
                .build();

        try {
            // Flushed here so a concurrent registration's unique violation surfaces in this method
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            if (isEmailUniqueViolation(e)) {
                throw new EmailAlreadyInUseException(EMAIL_IN_USE);
            }
            throw e;
        }

        String token = jwtService.generateToken(toAuthenticatedUser(user));

        return new AuthResponseDto(token, user.getDisplayName(), user.getId().toString(), user.getRole().name());
    }

    @Transactional(readOnly = true)
    public AuthResponseDto login(LoginRequestDto request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (user.getPassword() == null) {
            throw new IllegalArgumentException("Please login via Google");
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = jwtService.generateToken(toAuthenticatedUser(user));

        return new AuthResponseDto(token, user.getDisplayName(), user.getId().toString(), user.getRole().name());
    }

    @Transactional
    public AuthResponseDto authenticateWithGoogle(String googleIdTokenString) {
        GoogleIdToken.Payload payload = verifyGoogleToken(googleIdTokenString);

        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String pictureUrl = (String) payload.get("picture");

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = User.builder()
                    .email(email)
                    .displayName(name)
                    .role(UserRole.ROLE_USER)
                    .provider(AuthProvider.GOOGLE)
                    .avatarUrl(pictureUrl != null ? pictureUrl : "https://ui-avatars.com/api/?name=" + name)
                    .build();
            return userRepository.save(newUser);
        });

        if (user.getProvider() != AuthProvider.GOOGLE) {
            user.setProvider(AuthProvider.GOOGLE);
            if (user.getAvatarUrl() == null || user.getAvatarUrl().startsWith("https://ui-avatars.com")) {
                user.setAvatarUrl(pictureUrl);
            }
            userRepository.save(user);
        }

        String token = jwtService.generateToken(toAuthenticatedUser(user));
        return new AuthResponseDto(token, user.getDisplayName(), user.getId().toString(), user.getRole().name());
    }

    /**
     * Only the verifier's failures (malformed or invalid token, keys not fetched) are a 401; a failure after the
     * token was accepted, such as a database error, is a server error.
     */
    private GoogleIdToken.Payload verifyGoogleToken(String googleIdTokenString) {
        GoogleIdToken idToken;
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();
            idToken = verifier.verify(googleIdTokenString);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, GOOGLE_FAILED, e);
        }
        if (idToken == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, GOOGLE_FAILED);
        }
        return idToken.getPayload();
    }

    /**
     * Whether the insert hit the unique email constraint; the Hibernate exception is looked for in the whole cause
     * chain, as the translation that wraps it differs with the configured exception translator.
     */
    private static boolean isEmailUniqueViolation(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return EMAIL_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }

    private static AuthenticatedUser toAuthenticatedUser(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole().name());
    }
}

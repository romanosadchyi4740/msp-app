package com.romanosadchyi.smp.music_app.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.romanosadchyi.smp.music_app.model.dto.request.LoginRequestDto;
import com.romanosadchyi.smp.music_app.model.dto.request.RegisterRequestDto;
import com.romanosadchyi.smp.music_app.model.dto.response.AuthResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.enums.AuthProvider;
import com.romanosadchyi.smp.music_app.model.enums.UserRole;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import com.romanosadchyi.smp.music_app.shared.security.JwtService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthService {

    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    JwtService jwtService;

    @NonFinal
    @Value("${google.client.id}")
    String googleClientId;

    @Transactional
    public AuthResponseDto register(RegisterRequestDto request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already in use");
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .role(UserRole.ROLE_USER)
                .provider(AuthProvider.LOCAL)
                .avatarUrl("https://ui-avatars.com/api/?name=" + request.displayName())
                .build();

        user = userRepository.save(user);

        String token = jwtService.generateToken(toAuthenticatedUser(user));

        return new AuthResponseDto(token, user.getDisplayName(), user.getId().toString(), user.getRole().name());
    }

    @Transactional(readOnly = true)
    public AuthResponseDto login(LoginRequestDto request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (user.getPassword() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please login via Google");
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = jwtService.generateToken(toAuthenticatedUser(user));

        return new AuthResponseDto(token, user.getDisplayName(), user.getId().toString(), user.getRole().name());
    }

    @Transactional
    public AuthResponseDto authenticateWithGoogle(String googleIdTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(googleIdTokenString);
            if (idToken != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();

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
            } else {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid ID token.");
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Failed to authenticate with Google", e);
        }
    }

    private static AuthenticatedUser toAuthenticatedUser(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole().name());
    }

}

package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.dto.request.LoginRequestDto;
import com.romanosadchyi.smp.music_app.model.dto.request.RegisterRequestDto;
import com.romanosadchyi.smp.music_app.model.dto.response.AuthResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.enums.AuthProvider;
import com.romanosadchyi.smp.music_app.model.enums.UserRole;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthService {

    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    JwtService jwtService;

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

        String token = jwtService.generateToken(user);

        return new AuthResponseDto(token, user.getDisplayName(), user.getId().toString(), user.getRole().name());
    }

    public AuthResponseDto login(LoginRequestDto request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (user.getPassword() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please login via Google");
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = jwtService.generateToken(user);

        return new AuthResponseDto(token, user.getDisplayName(), user.getId().toString(), user.getRole().name());
    }

}

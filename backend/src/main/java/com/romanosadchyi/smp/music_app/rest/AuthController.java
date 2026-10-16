package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.dto.request.GoogleAuthRequestDto;
import com.romanosadchyi.smp.music_app.model.dto.request.LoginRequestDto;
import com.romanosadchyi.smp.music_app.model.dto.request.RegisterRequestDto;
import com.romanosadchyi.smp.music_app.model.dto.response.AuthResponseDto;
import com.romanosadchyi.smp.music_app.service.AuthService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthController {

    AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> register(@RequestBody RegisterRequestDto request) {
        log.info("Registering new user with email: {}", request.email());
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@RequestBody LoginRequestDto request) {
        log.info("Logging in user with email: {}", request.email());
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponseDto> loginWithGoogle(@RequestBody GoogleAuthRequestDto request) {
        log.info("Authenticating user via Google");
        return ResponseEntity.ok(authService.authenticateWithGoogle(request.googleToken()));
    }
}

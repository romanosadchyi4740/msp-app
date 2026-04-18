package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.dto.request.LoginRequestDto;
import com.romanosadchyi.smp.music_app.model.dto.request.RegisterRequestDto;
import com.romanosadchyi.smp.music_app.model.dto.response.AuthResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.enums.UserRole;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequestDto registerRequestDto;
    private LoginRequestDto loginRequestDto;
    private User testUser;

    @BeforeEach
    void setUp() {
        registerRequestDto = new RegisterRequestDto("test@example.com", "password", "Test User");
        loginRequestDto = new LoginRequestDto("test@example.com", "password");
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .password("encoded-password")
                .displayName("Test User")
                .role(UserRole.ROLE_USER)
                .build();
    }

    @Test
    void register_ShouldCreateUser_WhenEmailIsUnique() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateToken(any(User.class))).thenReturn("token");

        AuthResponseDto response = authService.register(registerRequestDto);

        assertNotNull(response);
        assertEquals("token", response.accessToken());
        assertEquals("Test User", response.displayName());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_ShouldThrowException_WhenEmailExists() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> authService.register(registerRequestDto));
    }

    @Test
    void login_ShouldReturnToken_WhenCredentialsAreValid() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtService.generateToken(any(User.class))).thenReturn("token");

        AuthResponseDto response = authService.login(loginRequestDto);

        assertNotNull(response);
        assertEquals("token", response.accessToken());
    }

    @Test
    void login_ShouldThrowException_WhenUserNotFound() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> authService.login(loginRequestDto));
    }

}

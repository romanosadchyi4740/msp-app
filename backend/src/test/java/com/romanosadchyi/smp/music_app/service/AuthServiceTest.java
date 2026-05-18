package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.dto.request.LoginRequestDto;
import com.romanosadchyi.smp.music_app.model.dto.request.RegisterRequestDto;
import com.romanosadchyi.smp.music_app.model.dto.response.AuthResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.enums.UserRole;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
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
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtService jwtService;

    @InjectMocks
    AuthService authService;

    @Test
    void register_ValidData_ReturnsToken() {
        RegisterRequestDto dto = new RegisterRequestDto("test@test.com", "pass123", "TestUser");
        when(userRepository.existsByEmail("test@test.com")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("encoded_pass");
        
        User savedUser = User.builder().id(UUID.randomUUID()).email("test@test.com").displayName("TestUser").role(UserRole.ROLE_USER).build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateToken(savedUser)).thenReturn("jwt_token");

        AuthResponseDto response = authService.register(dto);

        assertNotNull(response);
        assertEquals("jwt_token", response.accessToken());
        assertEquals("TestUser", response.displayName());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_EmailAlreadyExists_ThrowsResponseStatusException() {
        RegisterRequestDto dto = new RegisterRequestDto("test@test.com", "pass123", "TestUser");
        when(userRepository.existsByEmail("test@test.com")).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> authService.register(dto));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_ValidCredentials_ReturnsToken() {
        LoginRequestDto dto = new LoginRequestDto("test@test.com", "pass123");
        User user = User.builder().id(UUID.randomUUID()).email("test@test.com").password("encoded_pass").role(UserRole.ROLE_USER).displayName("TestUser").build();
        
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", "encoded_pass")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt_token");

        AuthResponseDto response = authService.login(dto);

        assertNotNull(response);
        assertEquals("jwt_token", response.accessToken());
    }

    @Test
    void login_InvalidPassword_ThrowsResponseStatusException() {
        LoginRequestDto dto = new LoginRequestDto("test@test.com", "wrongpass");
        User user = User.builder().id(UUID.randomUUID()).email("test@test.com").password("encoded_pass").role(UserRole.ROLE_USER).build();
        
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "encoded_pass")).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> authService.login(dto));
    }
}
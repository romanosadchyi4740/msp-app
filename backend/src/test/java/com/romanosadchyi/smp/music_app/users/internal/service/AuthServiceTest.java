package com.romanosadchyi.smp.music_app.users.internal.service;

import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import com.romanosadchyi.smp.music_app.shared.security.JwtService;
import com.romanosadchyi.smp.music_app.users.external.UserRole;
import com.romanosadchyi.smp.music_app.users.internal.dto.AuthResponseDto;
import com.romanosadchyi.smp.music_app.users.internal.dto.LoginRequestDto;
import com.romanosadchyi.smp.music_app.users.internal.dto.RegisterRequestDto;
import com.romanosadchyi.smp.music_app.users.internal.entity.User;
import com.romanosadchyi.smp.music_app.users.internal.exception.EmailAlreadyInUseException;
import com.romanosadchyi.smp.music_app.users.internal.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
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
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateToken(new AuthenticatedUser(savedUser.getId(), "test@test.com", "ROLE_USER"))).thenReturn("jwt_token");

        AuthResponseDto response = authService.register(dto);

        assertNotNull(response);
        assertEquals("jwt_token", response.accessToken());
        assertEquals("TestUser", response.displayName());
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    void register_EmailAlreadyExists_ThrowsEmailAlreadyInUse() {
        RegisterRequestDto dto = new RegisterRequestDto("test@test.com", "pass123", "TestUser");
        when(userRepository.existsByEmail("test@test.com")).thenReturn(true);

        EmailAlreadyInUseException ex = assertThrows(EmailAlreadyInUseException.class, () -> authService.register(dto));
        assertEquals("Email already in use", ex.getMessage());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void register_ConcurrentRegistrationWithTheEmail_ThrowsEmailAlreadyInUse() {
        RegisterRequestDto dto = new RegisterRequestDto("test@test.com", "pass123", "TestUser");
        when(userRepository.existsByEmail("test@test.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(uniqueViolation("email_unique"));

        assertThrows(EmailAlreadyInUseException.class, () -> authService.register(dto));
        verifyNoInteractions(jwtService);
    }

    @Test
    void register_EmailViolationDeeperInTheCauseChain_ThrowsEmailAlreadyInUse() {
        RegisterRequestDto dto = new RegisterRequestDto("test@test.com", "pass123", "TestUser");
        when(userRepository.existsByEmail("test@test.com")).thenReturn(false);
        DataIntegrityViolationException wrapped = new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("wrapper", uniqueViolation("email_unique").getCause()));
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(wrapped);

        assertThrows(EmailAlreadyInUseException.class, () -> authService.register(dto));
    }

    @Test
    void register_OtherConstraintViolation_IsRethrown() {
        RegisterRequestDto dto = new RegisterRequestDto("test@test.com", "pass123", "TestUser");
        when(userRepository.existsByEmail("test@test.com")).thenReturn(false);
        DataIntegrityViolationException violation = uniqueViolation("users_role_check");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(violation);

        assertSame(violation, assertThrows(DataIntegrityViolationException.class, () -> authService.register(dto)));
    }

    @Test
    void login_ValidCredentials_ReturnsToken() {
        LoginRequestDto dto = new LoginRequestDto("test@test.com", "pass123");
        User user = User.builder().id(UUID.randomUUID()).email("test@test.com").password("encoded_pass").role(UserRole.ROLE_USER).displayName("TestUser").build();
        
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", "encoded_pass")).thenReturn(true);
        when(jwtService.generateToken(new AuthenticatedUser(user.getId(), "test@test.com", "ROLE_USER"))).thenReturn("jwt_token");

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

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authService.login(dto));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Invalid credentials", ex.getReason());
    }

    @Test
    void login_UnknownEmail_ThrowsTheSameUnauthorized() {
        LoginRequestDto dto = new LoginRequestDto("nobody@test.com", "pass123");
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authService.login(dto));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Invalid credentials", ex.getReason());
    }

    @Test
    void login_GoogleUserWithNoPassword_ThrowsException() {
        LoginRequestDto dto = new LoginRequestDto("google@test.com", "pass123");
        User user = User.builder().id(UUID.randomUUID()).email("google@test.com").password(null).role(UserRole.ROLE_USER).build();
        
        when(userRepository.findByEmail("google@test.com")).thenReturn(Optional.of(user));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.login(dto));
        assertEquals("Please login via Google", ex.getMessage());
    }

    @Test
    void authenticateWithGoogle_MalformedToken_ThrowsUnauthorized() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.authenticateWithGoogle("not-a-token"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Failed to authenticate with Google", ex.getReason());
        verifyNoInteractions(userRepository);
    }

    private static DataIntegrityViolationException uniqueViolation(String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("violation", new SQLException("duplicate"), constraint));
    }
}

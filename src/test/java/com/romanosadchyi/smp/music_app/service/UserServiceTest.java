package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.UserNotFoundException;
import com.romanosadchyi.smp.music_app.model.dto.request.UserUpdateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.UserResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.enums.UserRole;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
import com.romanosadchyi.smp.music_app.service.mapping.UserMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMappingService userMappingService;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = spy(User.builder()
                .id(userId)
                .email("test@example.com")
                .displayName("Test User")
                .role(UserRole.ROLE_USER)
                .build());
    }

    @Test
    void getUserById_ShouldReturnUser_WhenUserExists() {
        UserResponseDto responseDto = UserResponseDto.builder()
                .id(userId)
                .displayName("Test User")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userMappingService.map(testUser)).thenReturn(responseDto);

        UserResponseDto result = userService.getUserById(userId);

        assertNotNull(result);
        assertEquals(userId, result.id());
        verify(userRepository).findById(userId);
    }

    @Test
    void getUserById_ShouldThrowException_WhenUserDoesNotExist() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getUserById(userId));
    }

    @Test
    void updateProfile_ShouldUpdateFields_WhenUserExists() {
        UserUpdateDto updateDto = new UserUpdateDto("Updated Name", "New Bio", "new-avatar.jpg");
        UserResponseDto responseDto = UserResponseDto.builder()
                .id(userId)
                .displayName("Updated Name")
                .biography("New Bio")
                .avatarUrl("new-avatar.jpg")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userMappingService.map(testUser)).thenReturn(responseDto);

        UserResponseDto result = userService.updateProfile(userId, updateDto);

        assertNotNull(result);
        assertEquals("Updated Name", result.displayName());
        assertEquals("New Bio", result.biography());
        verify(testUser).setDisplayName("Updated Name");
        verify(testUser).setBiography("New Bio");
        verify(testUser).setAvatarUrl("new-avatar.jpg");
    }

    @Test
    void becomeArtist_ShouldChangeRole_WhenUserExists() {
        UserResponseDto responseDto = UserResponseDto.builder()
                .id(userId)
                .role(UserRole.ROLE_ARTIST.name())
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userMappingService.map(testUser)).thenReturn(responseDto);

        UserResponseDto result = userService.becomeArtist(userId);

        assertNotNull(result);
        assertEquals(UserRole.ROLE_ARTIST.name(), result.role());
        verify(testUser).setRole(UserRole.ROLE_ARTIST);
    }

}

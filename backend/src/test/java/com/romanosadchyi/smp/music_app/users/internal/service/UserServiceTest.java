package com.romanosadchyi.smp.music_app.users.internal.service;

import com.romanosadchyi.smp.music_app.shared.security.SessionTerminator;
import com.romanosadchyi.smp.music_app.users.external.UserDataCleanup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;
import com.romanosadchyi.smp.music_app.users.external.UserRole;
import com.romanosadchyi.smp.music_app.users.internal.dto.CurrentUserResponseDto;
import com.romanosadchyi.smp.music_app.users.internal.dto.UserUpdateDto;
import com.romanosadchyi.smp.music_app.users.internal.entity.User;
import com.romanosadchyi.smp.music_app.users.internal.mapping.UserMappingService;
import com.romanosadchyi.smp.music_app.users.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    UserMappingService userMappingService;

    @Mock
    UserDataCleanup firstCleanup;

    @Mock
    UserDataCleanup secondCleanup;

    @Mock
    SessionTerminator sessionTerminator;

    UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, userMappingService, List.of(firstCleanup, secondCleanup),
                sessionTerminator);
    }

    @Test
    void getUserById_UserExists_ReturnsDto() {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).displayName("Test").build();
        when(userRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(user));
        when(userMappingService.map(user)).thenReturn(UserResponseDto.builder().id(id).displayName("Test").build());

        UserResponseDto res = userService.getUserById(id);

        assertNotNull(res);
        assertEquals("Test", res.displayName());
    }

    @Test
    void getUserById_UserNotFound_ThrowsException() {
        UUID id = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> userService.getUserById(id));
    }

    @Test
    void getCurrentUser_UserExists_ReturnsOwnProfile() {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).email("me@test.com").build();
        when(userRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(user));
        when(userMappingService.mapCurrentUser(user))
                .thenReturn(CurrentUserResponseDto.builder().id(id).email("me@test.com").build());

        CurrentUserResponseDto res = userService.getCurrentUser(id);

        assertEquals("me@test.com", res.email());
    }

    @Test
    void getCurrentUser_UserNotFound_ThrowsException() {
        UUID id = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> userService.getCurrentUser(id));
    }

    @Test
    void updateProfile_ValidData_UpdatesAndReturnsDto() {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).displayName("Old").build();
        UserUpdateDto dto = new UserUpdateDto("New", "Bio", "Url");

        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userMappingService.mapCurrentUser(any(User.class))).thenReturn(CurrentUserResponseDto.builder().id(id).displayName("New").biography("Bio").avatarUrl("Url").build());

        CurrentUserResponseDto res = userService.updateProfile(id, dto);

        assertNotNull(res);
        assertEquals("New", res.displayName());
        assertEquals("Bio", user.getBiography()); // Ensure mutation happened
        assertEquals("Url", user.getAvatarUrl());
        verify(userRepository).save(user);
    }

    @Test
    void becomeArtist_UserExists_UpdatesRole() {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).role(UserRole.ROLE_USER).build();

        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userMappingService.mapCurrentUser(user))
                .thenReturn(CurrentUserResponseDto.builder().id(id).role("ROLE_ARTIST").build());

        CurrentUserResponseDto res = userService.becomeArtist(id);

        assertEquals(UserRole.ROLE_ARTIST, user.getRole());
        assertEquals("ROLE_ARTIST", res.role());
        verify(userRepository).save(user);
    }

    @Test
    void followUser_ValidUsers_Follows() {
        UUID followerId = UUID.randomUUID();
        UUID followingId = UUID.randomUUID();

        User follower = User.builder().id(followerId).following(new HashSet<>()).build();
        User following = User.builder().id(followingId).build();

        when(userRepository.findByIdAndDeletedAtIsNull(followerId)).thenReturn(Optional.of(follower));
        when(userRepository.findByIdAndDeletedAtIsNull(followingId)).thenReturn(Optional.of(following));

        userService.followUser(followerId, followingId);

        assertTrue(follower.getFollowing().contains(following));
        verify(userRepository).save(follower);
    }

    @Test
    void followUser_DeletedAccount_IsNotFound() {
        UUID followerId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();
        User follower = User.builder().id(followerId).following(new HashSet<>()).build();
        when(userRepository.findByIdAndDeletedAtIsNull(followerId)).thenReturn(Optional.of(follower));
        when(userRepository.findByIdAndDeletedAtIsNull(deletedId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.followUser(followerId, deletedId));
        assertTrue(follower.getFollowing().isEmpty());
        verify(userRepository, never()).save(any());
    }

    @Test
    void followUser_SelfFollow_ThrowsException() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> userService.followUser(id, id));
    }

    @Test
    void unfollowUser_ValidUsers_Unfollows() {
        UUID followerId = UUID.randomUUID();
        UUID followingId = UUID.randomUUID();

        User following = User.builder().id(followingId).build();
        User follower = User.builder().id(followerId).following(new HashSet<>(List.of(following))).build();

        when(userRepository.findById(followerId)).thenReturn(Optional.of(follower));
        when(userRepository.findById(followingId)).thenReturn(Optional.of(following));

        userService.unfollowUser(followerId, followingId);

        assertFalse(follower.getFollowing().contains(following));
        verify(userRepository).save(follower);
    }

    @Test
    void terminateIdentity_UserExists_AnonymizesUserAndPurgesContent() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("real@email.com")
                .password("real-password")
                .displayName("Real Name")
                .avatarUrl("real-avatar.jpg")
                .biography("Real biography")
                .providerId("real-provider")
                .followers(new HashSet<>(List.of(User.builder().build())))
                .following(new HashSet<>(List.of(User.builder().build())))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.terminateIdentity(userId);

        InOrder order = inOrder(firstCleanup, secondCleanup, userRepository);
        order.verify(firstCleanup).deleteUserData(userId);
        order.verify(secondCleanup).deleteUserData(userId);
        order.verify(userRepository).save(user);

        assertTrue(user.getFollowing().isEmpty());
        assertTrue(user.getFollowers().isEmpty());

        assertTrue(user.getEmail().startsWith("deleted_"));
        assertNotEquals("real-password", user.getPassword());
        assertEquals("Disconnected Node", user.getDisplayName());
        assertNull(user.getAvatarUrl());
        assertNotNull(user.getDeletedAt());
    }

    @Test
    void terminateIdentity_WithoutTransaction_ClosesTheUsersSessions() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));

        userService.terminateIdentity(userId);

        verify(sessionTerminator).closeSessionsOf(userId);
    }

    @Test
    void terminateIdentity_InTransaction_ClosesTheUsersSessionsAfterCommit() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        TransactionSynchronizationManager.initSynchronization();
        try {
            userService.terminateIdentity(userId);
            verify(sessionTerminator, never()).closeSessionsOf(any());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            verify(sessionTerminator).closeSessionsOf(userId);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void terminateIdentity_UserNotFound_CallsNoCleanup() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.terminateIdentity(userId));
        verifyNoInteractions(firstCleanup, secondCleanup, sessionTerminator);
    }
}

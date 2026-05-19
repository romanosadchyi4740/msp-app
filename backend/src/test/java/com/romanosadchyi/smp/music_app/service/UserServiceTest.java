package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.UserNotFoundException;
import com.romanosadchyi.smp.music_app.model.dto.request.UserUpdateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.UserResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.enums.UserRole;
import com.romanosadchyi.smp.music_app.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.repository.PlaylistRepository;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
import com.romanosadchyi.smp.music_app.service.mapping.UserMappingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    TrackRepository trackRepository;

    @Mock
    AlbumRepository albumRepository;

    @Mock
    PlaylistRepository playlistRepository;

    @Mock
    UserMappingService userMappingService;

    @InjectMocks
    UserService userService;

    @Test
    void getUserById_UserExists_ReturnsDto() {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).displayName("Test").build();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userMappingService.map(user)).thenReturn(UserResponseDto.builder().id(id).displayName("Test").build());

        UserResponseDto res = userService.getUserById(id);
        
        assertNotNull(res);
        assertEquals("Test", res.displayName());
    }

    @Test
    void getUserById_UserNotFound_ThrowsException() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> userService.getUserById(id));
    }

    @Test
    void updateProfile_ValidData_UpdatesAndReturnsDto() {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).displayName("Old").build();
        UserUpdateDto dto = new UserUpdateDto("New", "Bio", "Url");

        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userMappingService.map(any(User.class))).thenReturn(UserResponseDto.builder().id(id).displayName("New").biography("Bio").avatarUrl("Url").build());

        UserResponseDto res = userService.updateProfile(id, dto);

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

        userService.becomeArtist(id);

        assertEquals(UserRole.ROLE_ARTIST, user.getRole());
        verify(userRepository).save(user);
    }

    @Test
    void followUser_ValidUsers_Follows() {
        UUID followerId = UUID.randomUUID();
        UUID followingId = UUID.randomUUID();
        
        User follower = User.builder().id(followerId).following(new HashSet<>()).build();
        User following = User.builder().id(followingId).build();

        when(userRepository.findById(followerId)).thenReturn(Optional.of(follower));
        when(userRepository.findById(followingId)).thenReturn(Optional.of(following));

        userService.followUser(followerId, followingId);

        assertTrue(follower.getFollowing().contains(following));
        verify(userRepository).save(follower);
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
                .likedTracks(new HashSet<>(List.of(Track.builder().build())))
                .followers(new HashSet<>(List.of(User.builder().build())))
                .following(new HashSet<>(List.of(User.builder().build())))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(playlistRepository.findAllByOwnerId(userId)).thenReturn(List.of());
        when(albumRepository.findAllByAuthorId(userId)).thenReturn(List.of());
        when(trackRepository.findAllByAuthorId(userId)).thenReturn(List.of());

        userService.terminateIdentity(userId);

        verify(playlistRepository).deleteAll(anyList());
        verify(albumRepository).deleteAll(anyList());
        verify(trackRepository).deleteAll(anyList());

        assertTrue(user.getLikedTracks().isEmpty());
        assertTrue(user.getFollowing().isEmpty());
        assertTrue(user.getFollowers().isEmpty());

        assertTrue(user.getEmail().startsWith("deleted_"));
        assertNotEquals("real-password", user.getPassword());
        assertEquals("Disconnected Node", user.getDisplayName());
        assertNull(user.getAvatarUrl());
        
        verify(userRepository).save(user);
    }
}
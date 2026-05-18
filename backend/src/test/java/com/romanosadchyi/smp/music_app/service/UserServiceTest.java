package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.entity.Track;
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

        // Verify Content Purge
        verify(playlistRepository).deleteAll(anyList());
        verify(albumRepository).deleteAll(anyList());
        verify(trackRepository).deleteAll(anyList());

        // Verify Social Purge
        assertTrue(user.getLikedTracks().isEmpty());
        assertTrue(user.getFollowing().isEmpty());
        assertTrue(user.getFollowers().isEmpty());

        // Verify Anonymization
        assertTrue(user.getEmail().startsWith("deleted_"));
        assertTrue(user.getEmail().endsWith("@system.local"));
        assertNotEquals("real-password", user.getPassword());
        assertNotNull(user.getPassword());
        assertEquals("Disconnected Node", user.getDisplayName());
        assertNull(user.getAvatarUrl());
        assertNull(user.getBiography());
        assertNull(user.getProviderId());
        assertNull(user.getProvider());

        verify(userRepository).save(user);
    }
}

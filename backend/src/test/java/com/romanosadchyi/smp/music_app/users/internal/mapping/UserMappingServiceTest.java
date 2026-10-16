package com.romanosadchyi.smp.music_app.users.internal.mapping;

import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;
import com.romanosadchyi.smp.music_app.users.external.UserRole;
import com.romanosadchyi.smp.music_app.users.internal.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserMappingServiceTest {

    private final UserMappingService userMappingService = new UserMappingService();

    @Test
    void map_ReturnsDto() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        User follower = User.builder().id(UUID.randomUUID()).build();
        User following = User.builder().id(UUID.randomUUID()).build();
        
        User user = User.builder()
                .id(id)
                .email("test@test.com")
                .displayName("Name")
                .avatarUrl("avatar")
                .biography("bio")
                .role(UserRole.ROLE_ARTIST)
                .createdAt(now)
                .followers(new HashSet<>(List.of(follower)))
                .following(new HashSet<>(List.of(following)))
                .build();

        UserResponseDto result = userMappingService.map(user);

        assertNotNull(result);
        assertEquals(id, result.id());
        assertEquals("test@test.com", result.email());
        assertEquals("Name", result.displayName());
        assertEquals("avatar", result.avatarUrl());
        assertEquals("bio", result.biography());
        assertEquals("ROLE_ARTIST", result.role());
        assertEquals(now, result.createdAt());
        assertEquals(1, result.followersCount());
        assertEquals(1, result.followingCount());
    }

    @Test
    void map_NullCollections_ReturnsZeroCounts() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .role(UserRole.ROLE_USER)
                .followers(null)
                .following(null)
                .build();

        UserResponseDto result = userMappingService.map(user);

        assertEquals(0, result.followersCount());
        assertEquals(0, result.followingCount());
    }
}

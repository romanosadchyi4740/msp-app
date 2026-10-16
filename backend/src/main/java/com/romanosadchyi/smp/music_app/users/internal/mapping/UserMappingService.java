package com.romanosadchyi.smp.music_app.users.internal.mapping;

import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
import com.romanosadchyi.smp.music_app.users.internal.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMappingService {

    public UserResponseDto map(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .biography(user.getBiography())
                .role(user.getRole().name())
                .createdAt(user.getCreatedAt())
                .followersCount(user.getFollowers() != null ? user.getFollowers().size() : 0)
                .followingCount(user.getFollowing() != null ? user.getFollowing().size() : 0)
                .build();
    }

    public UserSummary toSummary(User user) {
        return new UserSummary(user.getId(), user.getDisplayName(), user.getAvatarUrl(), user.getRole());
    }
}

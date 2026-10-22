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
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {

    UserRepository userRepository;
    UserMappingService userMappingService;
    List<UserDataCleanup> userDataCleanups;
    SessionTerminator sessionTerminator;

    @Transactional(readOnly = true)
    public UserResponseDto getUserById(UUID id) {
        return userMappingService.map(getActiveUser(id, "User not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponseDto getCurrentUser(UUID id) {
        return userMappingService.mapCurrentUser(getActiveUser(id, "User not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Page<UserResponseDto> searchUsers(String query, int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        return userRepository.findAllByDisplayNameContainingIgnoreCaseAndDeletedAtIsNull(query, pageable)
                .map(userMappingService::map);
    }

    @Transactional
    public CurrentUserResponseDto updateProfile(UUID id, UserUpdateDto dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        if (dto.displayName() != null) {
            user.setDisplayName(dto.displayName());
        }
        if (dto.biography() != null) {
            user.setBiography(dto.biography());
        }
        if (dto.avatarUrl() != null) {
            user.setAvatarUrl(dto.avatarUrl());
        }

        return userMappingService.mapCurrentUser(userRepository.save(user));
    }

    @Transactional
    public CurrentUserResponseDto becomeArtist(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        user.setRole(UserRole.ROLE_ARTIST);
        return userMappingService.mapCurrentUser(userRepository.save(user));
    }

    @Transactional
    public void followUser(UUID followerId, UUID followingId) {
        if (followerId.equals(followingId)) {
            throw new IllegalArgumentException("User cannot follow themselves");
        }
        User follower = getActiveUser(followerId, "Follower not found");
        User following = getActiveUser(followingId, "User to follow not found");

        follower.getFollowing().add(following);
        userRepository.save(follower);
    }

    @Transactional
    public void unfollowUser(UUID followerId, UUID followingId) {
        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new UserNotFoundException("Follower not found"));
        User following = userRepository.findById(followingId)
                .orElseThrow(() -> new UserNotFoundException("User to unfollow not found"));

        follower.getFollowing().remove(following);
        userRepository.save(follower);
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> getFollowing(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return user.getFollowing().stream()
                .map(userMappingService::map)
                .toList();
    }

    @Transactional
    public void terminateIdentity(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        // 1. Purge content and every other module's data linked to the user
        userDataCleanups.forEach(cleanup -> cleanup.deleteUserData(userId));

        // 2. Social Purge
        user.getFollowing().clear();
        user.getFollowers().clear();

        // 3. Anonymize (Soft Delete)
        user.setEmail("deleted_" + UUID.randomUUID() + "@system.local");
        user.setPassword(UUID.randomUUID().toString()); // Secure random string
        user.setDisplayName("Disconnected Node");
        user.setAvatarUrl(null);
        user.setBiography(null);
        user.setProviderId(null);
        user.setProvider(null);
        user.setDeletedAt(Instant.now());

        userRepository.save(user);
        closeSessionsAfterCommit(userId);
    }

    /**
     * Closes the user's WebSocket sessions once the deletion is committed, so their reconnect is refused; a
     * rolled-back deletion leaves them connected.
     */
    private void closeSessionsAfterCommit(UUID userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sessionTerminator.closeSessionsOf(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sessionTerminator.closeSessionsOf(userId);
            }
        });
    }

    /**
     * A deleted account is not found: its anonymized row stays only for other modules' references.
     */
    private User getActiveUser(UUID id, String notFoundMessage) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new UserNotFoundException(notFoundMessage));
    }
}

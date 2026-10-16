package com.romanosadchyi.smp.music_app.users.internal.service;

import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
import com.romanosadchyi.smp.music_app.users.internal.entity.User;
import com.romanosadchyi.smp.music_app.users.internal.mapping.UserMappingService;
import com.romanosadchyi.smp.music_app.users.internal.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class UserLookupService implements UserLookup {

    UserRepository userRepository;
    UserMappingService userMappingService;

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID userId) {
        return userRepository.existsByIdAndDeletedAtIsNull(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummary> findSummary(UUID userId) {
        return userRepository.findById(userId).map(userMappingService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public UserSummary getSummary(UUID userId) {
        return userMappingService.toSummary(getUser(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UserSummary> findSummaries(Collection<UUID> userIds) {
        return userRepository.findAllById(userIds).stream()
                .map(userMappingService::toSummary)
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDto getProfile(UUID userId) {
        return userMappingService.map(getUser(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponseDto> searchProfiles(String displayNameQuery, Pageable pageable) {
        return userRepository.findAllByDisplayNameContainingIgnoreCaseAndDeletedAtIsNull(displayNameQuery, pageable)
                .map(userMappingService::map);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findFollowingIds(UUID userId) {
        return userRepository.findFollowingIds(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findDeletedIds(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return userRepository.findDeletedIds(userIds);
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
    }
}

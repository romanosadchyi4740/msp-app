package com.romanosadchyi.smp.music_app.users.external;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Read access to users for other modules, which store only user ids.
 */
public interface UserLookup {

    /**
     * Whether {@code userId} is an account that can act or be addressed: false for unknown ids and for deleted
     * accounts, whose anonymized rows the other lookups still return for display.
     */
    boolean exists(UUID userId);

    Optional<UserSummary> findSummary(UUID userId);

    /**
     * @throws UserNotFoundException if there is no such user
     */
    UserSummary getSummary(UUID userId);

    /**
     * Ids without a user are missing from the result.
     */
    Map<UUID, UserSummary> findSummaries(Collection<UUID> userIds);

    /**
     * The public profile, as embedded in chat, message, feed and integration request responses.
     *
     * @throws UserNotFoundException if there is no such user
     */
    UserResponseDto getProfile(UUID userId);

    /**
     * The public profiles of the given users, by id, in one query; ids without a user are missing from the result.
     * Lists that embed a profile per row use this instead of {@link #getProfile}.
     */
    Map<UUID, UserResponseDto> findProfiles(Collection<UUID> userIds);

    Page<UserResponseDto> searchProfiles(String displayNameQuery, Pageable pageable);

    /**
     * Ids of the users that {@code userId} follows.
     */
    Set<UUID> findFollowingIds(UUID userId);

    /**
     * The given users whose accounts are deleted. A deleted account's row stays, anonymized, so the other lookups
     * still find it.
     */
    Set<UUID> findDeletedIds(Collection<UUID> userIds);
}

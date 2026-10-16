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

    Page<UserResponseDto> searchProfiles(String displayNameQuery, Pageable pageable);

    /**
     * Ids of the users that {@code userId} follows.
     */
    Set<UUID> findFollowingIds(UUID userId);
}

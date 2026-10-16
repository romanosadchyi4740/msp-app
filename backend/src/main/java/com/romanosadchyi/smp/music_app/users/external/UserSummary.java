package com.romanosadchyi.smp.music_app.users.external;

import java.util.UUID;

/**
 * The few user fields other modules show next to their own data, e.g. a track's author.
 */
public record UserSummary(UUID id, String displayName, String avatarUrl, UserRole role) {
}

package com.romanosadchyi.smp.music_app.users.external;

import java.util.UUID;

/**
 * Implemented by every module that stores data linked to a user. Account deletion calls all implementations in
 * the same transaction, before the user's own data is removed.
 */
public interface UserDataCleanup {

    void deleteUserData(UUID userId);
}

package com.romanosadchyi.smp.music_app.users.internal.service;

import com.romanosadchyi.smp.music_app.shared.security.AccountStatus;
import com.romanosadchyi.smp.music_app.users.internal.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * "Active" means the same as {@code UserLookup.exists}: the account exists and is not deleted.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class UserAccountStatus implements AccountStatus {

    UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(UUID userId) {
        return userRepository.existsByIdAndDeletedAtIsNull(userId);
    }
}

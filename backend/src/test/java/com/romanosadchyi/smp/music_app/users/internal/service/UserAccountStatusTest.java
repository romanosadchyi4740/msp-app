package com.romanosadchyi.smp.music_app.users.internal.service;

import com.romanosadchyi.smp.music_app.users.internal.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountStatusTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    UserAccountStatus accountStatus;

    @Test
    void isActive_OnlyForAnExistingAccountThatIsNotDeleted() {
        UUID active = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        when(userRepository.existsByIdAndDeletedAtIsNull(active)).thenReturn(true);
        when(userRepository.existsByIdAndDeletedAtIsNull(deleted)).thenReturn(false);

        assertThat(accountStatus.isActive(active)).isTrue();
        assertThat(accountStatus.isActive(deleted)).isFalse();
    }
}

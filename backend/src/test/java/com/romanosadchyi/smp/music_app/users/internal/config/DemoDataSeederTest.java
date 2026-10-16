package com.romanosadchyi.smp.music_app.users.internal.config;

import com.romanosadchyi.smp.music_app.users.external.UserRole;
import com.romanosadchyi.smp.music_app.users.internal.entity.User;
import com.romanosadchyi.smp.music_app.users.internal.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DemoDataSeederTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    DemoDataSeeder demoDataSeeder;

    @Test
    void run_NoUsers_CreatesDemoArtistWithGeneratedId() {
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        demoDataSeeder.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(DemoDataSeeder.DEMO_ARTIST_EMAIL, captor.getValue().getEmail());
        assertEquals(UserRole.ROLE_ARTIST, captor.getValue().getRole());
        assertNull(captor.getValue().getId());
    }

    @Test
    void run_UsersExist_DoesNothing() {
        when(userRepository.count()).thenReturn(1L);

        demoDataSeeder.run();

        verify(userRepository, never()).save(any());
    }
}

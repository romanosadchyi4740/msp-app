package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.entity.Playlist;
import com.romanosadchyi.smp.music_app.repository.PlaylistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyUserDataCleanupTest {

    @Mock
    PlaylistRepository playlistRepository;

    @InjectMocks
    LegacyUserDataCleanup cleanup;

    @Test
    void deleteUserData_DeletesPlaylists() {
        UUID userId = UUID.randomUUID();
        List<Playlist> playlists = List.of(new Playlist());
        when(playlistRepository.findAllByOwnerId(userId)).thenReturn(playlists);

        cleanup.deleteUserData(userId);

        verify(playlistRepository).deleteAll(playlists);
    }
}

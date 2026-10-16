package com.romanosadchyi.smp.music_app.playlists.internal.service;

import com.romanosadchyi.smp.music_app.playlists.internal.entity.IntegrationRequest;
import com.romanosadchyi.smp.music_app.playlists.internal.entity.Playlist;
import com.romanosadchyi.smp.music_app.playlists.internal.repository.IntegrationRequestRepository;
import com.romanosadchyi.smp.music_app.playlists.internal.repository.PlaylistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaylistsUserDataCleanupTest {

    @Mock
    PlaylistRepository playlistRepository;

    @Mock
    IntegrationRequestRepository integrationRequestRepository;

    @InjectMocks
    PlaylistsUserDataCleanup cleanup;

    @Test
    void deleteUserData_DeletesSentRequests() {
        UUID userId = UUID.randomUUID();
        List<IntegrationRequest> requests = List.of(new IntegrationRequest());
        when(integrationRequestRepository.findAllByRequesterId(userId)).thenReturn(requests);

        cleanup.deleteUserData(userId);

        verify(integrationRequestRepository).deleteAll(requests);
    }

    @Test
    void deleteUserData_DetachesForksThenDeletesEachPlaylist() {
        UUID userId = UUID.randomUUID();
        Playlist first = new Playlist();
        Playlist second = new Playlist();
        when(playlistRepository.findAllByOwnerId(userId)).thenReturn(List.of(first, second));

        cleanup.deleteUserData(userId);

        InOrder order = inOrder(playlistRepository);
        order.verify(playlistRepository).detachParentPlaylist(first);
        order.verify(playlistRepository).delete(first);
        order.verify(playlistRepository).detachParentPlaylist(second);
        order.verify(playlistRepository).delete(second);
    }
}

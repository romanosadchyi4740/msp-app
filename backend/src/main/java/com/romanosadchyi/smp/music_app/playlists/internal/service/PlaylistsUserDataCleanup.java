package com.romanosadchyi.smp.music_app.playlists.internal.service;

import com.romanosadchyi.smp.music_app.playlists.internal.entity.Playlist;
import com.romanosadchyi.smp.music_app.playlists.internal.repository.IntegrationRequestRepository;
import com.romanosadchyi.smp.music_app.playlists.internal.repository.PlaylistRepository;
import com.romanosadchyi.smp.music_app.users.external.UserDataCleanup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * On account deletion: deletes the integration requests the user sent and the user's playlists. Other users' forks
 * of those playlists stay and lose their origin; requests to the deleted playlists go with them.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class PlaylistsUserDataCleanup implements UserDataCleanup {

    PlaylistRepository playlistRepository;
    IntegrationRequestRepository integrationRequestRepository;

    @Override
    public void deleteUserData(UUID userId) {
        integrationRequestRepository.deleteAll(integrationRequestRepository.findAllByRequesterId(userId));
        for (Playlist playlist : playlistRepository.findAllByOwnerId(userId)) {
            playlistRepository.detachParentPlaylist(playlist);
            playlistRepository.delete(playlist);
        }
    }
}

package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.repository.PlaylistRepository;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.users.external.UserDataCleanup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Deletes a user's playlists, albums, tracks and likes on account deletion. It moves into the music and playlists
 * modules, split by the data each owns, when they are extracted (KAN-64).
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LegacyUserDataCleanup implements UserDataCleanup {

    PlaylistRepository playlistRepository;
    AlbumRepository albumRepository;
    TrackRepository trackRepository;

    @Override
    public void deleteUserData(UUID userId) {
        playlistRepository.deleteAll(playlistRepository.findAllByOwnerId(userId));
        albumRepository.deleteAll(albumRepository.findAllByAuthorId(userId));
        trackRepository.deleteAll(trackRepository.findAllByAuthorId(userId));
        trackRepository.deleteLikesByUser(userId);
    }
}

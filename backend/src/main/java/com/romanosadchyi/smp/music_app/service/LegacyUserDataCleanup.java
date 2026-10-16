package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.repository.PlaylistRepository;
import com.romanosadchyi.smp.music_app.users.external.UserDataCleanup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Deletes a user's playlists on account deletion. It moves into the playlists module when that is extracted
 * (KAN-69); music deletes its own data in {@code MusicUserDataCleanup}.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LegacyUserDataCleanup implements UserDataCleanup {

    PlaylistRepository playlistRepository;

    @Override
    public void deleteUserData(UUID userId) {
        playlistRepository.deleteAll(playlistRepository.findAllByOwnerId(userId));
    }
}

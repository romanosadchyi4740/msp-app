package com.romanosadchyi.smp.music_app.music.internal.service;

import com.romanosadchyi.smp.music_app.music.internal.entity.Album;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.music.internal.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.music.internal.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.users.external.UserDataCleanup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * On account deletion: deletes the user's albums and tracks (with their audio files and references) and their likes.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class MusicUserDataCleanup implements UserDataCleanup {

    AlbumRepository albumRepository;
    TrackRepository trackRepository;
    TrackRemover trackRemover;

    @Override
    public void deleteUserData(UUID userId) {
        for (Album album : albumRepository.findAllByAuthorId(userId)) {
            album.getTracks().forEach(trackRemover::releaseTrack);
            albumRepository.delete(album);
        }
        for (Track track : trackRepository.findAllByAuthorId(userId)) {
            trackRemover.releaseTrack(track);
            trackRepository.delete(track);
        }
        trackRepository.deleteLikesByUser(userId);
    }
}

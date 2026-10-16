package com.romanosadchyi.smp.music_app.music.internal.service;

import com.romanosadchyi.smp.music_app.music.external.TrackDeletionCleanup;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.music.internal.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.storage.external.FileStorage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Prepares a track for deletion: removes its audio file, lets every {@link TrackDeletionCleanup} drop references
 * to it (playlists and chat messages have no cascading foreign key), and unlinks its genres. The caller then
 * deletes the track row, directly or through its album.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class TrackRemover {

    TrackRepository trackRepository;
    FileStorage fileStorage;
    List<TrackDeletionCleanup> trackDeletionCleanups;

    void releaseTrack(Track track) {
        fileStorage.delete(track.getFileKey());
        trackDeletionCleanups.forEach(cleanup -> cleanup.deleteTrackReferences(track.getId()));
        trackRepository.deleteFromTrackGenres(track.getId());
    }
}

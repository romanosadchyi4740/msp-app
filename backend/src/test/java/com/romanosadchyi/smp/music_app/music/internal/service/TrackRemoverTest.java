package com.romanosadchyi.smp.music_app.music.internal.service;

import com.romanosadchyi.smp.music_app.music.external.TrackDeletionCleanup;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.music.internal.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.storage.external.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TrackRemoverTest {

    @Mock
    TrackRepository trackRepository;

    @Mock
    FileStorage fileStorage;

    @Mock
    TrackDeletionCleanup firstCleanup;

    @Mock
    TrackDeletionCleanup secondCleanup;

    TrackRemover trackRemover;

    @BeforeEach
    void setUp() {
        trackRemover = new TrackRemover(trackRepository, fileStorage, List.of(firstCleanup, secondCleanup));
    }

    @Test
    void releaseTrack_DeletesFileReferencesAndGenres() {
        Track track = Track.builder().id(UUID.randomUUID()).fileKey("tracks/1.mp3").build();

        trackRemover.releaseTrack(track);

        verify(fileStorage).delete("tracks/1.mp3");
        verify(firstCleanup).deleteTrackReferences(track.getId());
        verify(secondCleanup).deleteTrackReferences(track.getId());
        verify(trackRepository).deleteFromTrackGenres(track.getId());
    }
}

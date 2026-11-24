package com.romanosadchyi.smp.music_app.music.internal.service;

import com.romanosadchyi.smp.music_app.music.internal.entity.Album;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.music.internal.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.music.internal.repository.TrackRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MusicUserDataCleanupTest {

    @Mock
    AlbumRepository albumRepository;

    @Mock
    TrackRepository trackRepository;

    @Mock
    TrackRemover trackRemover;

    @InjectMocks
    MusicUserDataCleanup cleanup;

    @Test
    void deleteUserData_ReleasesTracksBeforeDeletingAlbumsAndTracks() {
        UUID userId = UUID.randomUUID();
        Track albumTrack = Track.builder().id(UUID.randomUUID()).build();
        Album album = Album.builder().id(UUID.randomUUID()).tracks(new ArrayList<>(List.of(albumTrack))).build();
        Track looseTrack = Track.builder().id(UUID.randomUUID()).build();
        when(albumRepository.findAllByAuthorId(userId)).thenReturn(List.of(album));
        when(trackRepository.findAllByAuthorId(userId)).thenReturn(List.of(looseTrack));

        cleanup.deleteUserData(userId);

        InOrder order = inOrder(trackRemover, albumRepository, trackRepository);
        order.verify(trackRemover).releaseTrack(albumTrack);
        order.verify(albumRepository).delete(album);
        order.verify(trackRemover).releaseTrack(looseTrack);
        order.verify(trackRepository).delete(looseTrack);
        verify(trackRepository).deleteLikesByUser(userId);
    }
}

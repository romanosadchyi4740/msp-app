package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.entity.Album;
import com.romanosadchyi.smp.music_app.model.entity.Playlist;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.repository.PlaylistRepository;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
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

    @Mock
    AlbumRepository albumRepository;

    @Mock
    TrackRepository trackRepository;

    @InjectMocks
    LegacyUserDataCleanup cleanup;

    @Test
    void deleteUserData_DeletesPlaylistsAlbumsTracksAndLikes() {
        UUID userId = UUID.randomUUID();
        List<Playlist> playlists = List.of(new Playlist());
        List<Album> albums = List.of(new Album());
        List<Track> tracks = List.of(new Track());
        when(playlistRepository.findAllByOwnerId(userId)).thenReturn(playlists);
        when(albumRepository.findAllByAuthorId(userId)).thenReturn(albums);
        when(trackRepository.findAllByAuthorId(userId)).thenReturn(tracks);

        cleanup.deleteUserData(userId);

        verify(playlistRepository).deleteAll(playlists);
        verify(albumRepository).deleteAll(albums);
        verify(trackRepository).deleteAll(tracks);
        verify(trackRepository).deleteLikesByUser(userId);
    }
}

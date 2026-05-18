package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.AlbumNotFoundException;
import com.romanosadchyi.smp.music_app.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.model.dto.request.AlbumCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.AlbumResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Album;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.enums.UserRole;
import com.romanosadchyi.smp.music_app.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.repository.MessageRepository;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
import com.romanosadchyi.smp.music_app.service.mapping.AlbumMappingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlbumServiceTest {

    @Mock
    AlbumRepository albumRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    TrackRepository trackRepository;

    @Mock
    MessageRepository messageRepository;

    @Mock
    MinioService minioService;

    @Mock
    AlbumMappingService albumMappingService;

    @InjectMocks
    AlbumService albumService;

    @Test
    void createAlbum_WhenUserIsArtist_SavesAlbum() {
        UUID authorId = UUID.randomUUID();
        User author = User.builder().id(authorId).role(UserRole.ROLE_ARTIST).build();
        AlbumCreateDto dto = new AlbumCreateDto("Test Album", "Desc", "url");

        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(albumRepository.save(any(Album.class))).thenAnswer(i -> i.getArgument(0));
        when(albumMappingService.map(any(Album.class))).thenReturn(new AlbumResponseDto(null, "Test Album", "Desc", "url", authorId, null, null, null));

        AlbumResponseDto response = albumService.createAlbum(authorId, dto);

        assertNotNull(response);
        assertEquals("Test Album", response.title());
        verify(albumRepository).save(any(Album.class));
    }

    @Test
    void createAlbum_WhenUserIsNotArtist_ThrowsException() {
        UUID authorId = UUID.randomUUID();
        User author = User.builder().id(authorId).role(UserRole.ROLE_USER).build();
        AlbumCreateDto dto = new AlbumCreateDto("Test Album", "Desc", "url");

        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));

        assertThrows(UserNotAuthorizedException.class, () -> albumService.createAlbum(authorId, dto));
        verify(albumRepository, never()).save(any(Album.class));
    }

    @Test
    void deleteAlbum_WhenUserIsOwner_CascadesDeletions() {
        UUID authorId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        
        User author = User.builder().id(authorId).build();
        Track track = Track.builder().id(UUID.randomUUID()).fileKey("file.mp3").build();
        Album album = Album.builder()
                .id(albumId)
                .title("To Delete")
                .author(author)
                .tracks(new ArrayList<>(java.util.List.of(track)))
                .build();

        when(albumRepository.findById(albumId)).thenReturn(Optional.of(album));

        albumService.deleteAlbum(albumId, authorId);

        verify(minioService).deleteFile("file.mp3");
        verify(messageRepository).detachTrack(track);
        verify(trackRepository).deleteFromPlaylistTracks(track.getId());
        verify(trackRepository).deleteFromTrackGenres(track.getId());
        verify(albumRepository).delete(album);
    }

    @Test
    void deleteAlbum_WhenUserIsNotOwner_ThrowsException() {
        UUID authorId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        
        User author = User.builder().id(authorId).build();
        Album album = Album.builder().id(albumId).author(author).build();

        when(albumRepository.findById(albumId)).thenReturn(Optional.of(album));

        assertThrows(UserNotAuthorizedException.class, () -> albumService.deleteAlbum(albumId, otherUserId));
        verify(albumRepository, never()).delete(any(Album.class));
    }
}

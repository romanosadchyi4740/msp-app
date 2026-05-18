package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.MusicTrackFileAlreadyExistsException;
import com.romanosadchyi.smp.music_app.model.dto.request.TrackCreateDto;
import com.romanosadchyi.smp.music_app.model.entity.Album;
import com.romanosadchyi.smp.music_app.model.entity.Genre;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.enums.UserRole;
import com.romanosadchyi.smp.music_app.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.repository.GenreRepository;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
import com.romanosadchyi.smp.music_app.service.mapping.TrackMappingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackServiceTest {

    @Mock
    TrackRepository trackRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    AlbumRepository albumRepository;

    @Mock
    GenreRepository genreRepository;

    @Mock
    MinioService minioService;

    @Mock
    TrackMappingService trackMappingService;

    @InjectMocks
    TrackService trackService;

    @Test
    void createTrack_ValidData_UploadsToMinioAndSavesTrack() throws Exception {
        UUID authorId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        User author = User.builder().id(authorId).role(UserRole.ROLE_ARTIST).build();
        Album album = Album.builder().id(albumId).author(author).coverUrl("album-cover.jpg").build();
        Genre genre = Genre.builder().id(UUID.randomUUID()).build();
        
        TrackCreateDto dto = new TrackCreateDto("Test Track", 120, null, albumId, null, java.util.Set.of(genre.getId()));
        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", "audio/mpeg", "test data".getBytes());

        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(albumRepository.findById(albumId)).thenReturn(Optional.of(album));
        when(genreRepository.findAllById(dto.genreIds())).thenReturn(List.of(genre));
        when(trackRepository.existsByFileHash(anyString())).thenReturn(false);
        
        Track mappedTrack = Track.builder().build();
        when(trackMappingService.map(dto)).thenReturn(mappedTrack);
        
        Track savedTrack = Track.builder().id(UUID.randomUUID()).coverUrl(null).build();
        when(trackRepository.save(any(Track.class))).thenReturn(savedTrack);

        trackService.createTrack(authorId, dto, file);

        verify(minioService).uploadFile(eq(file), anyString());
        verify(trackRepository, times(2)).save(any(Track.class));
    }

    @Test
    void createTrack_DuplicateFileHash_ThrowsConflictException() throws Exception {
        UUID authorId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        User author = User.builder().id(authorId).role(UserRole.ROLE_ARTIST).build();
        Album album = Album.builder().id(albumId).author(author).coverUrl("album-cover.jpg").build();
        Genre genre = Genre.builder().id(UUID.randomUUID()).build();
        
        TrackCreateDto dto = new TrackCreateDto("Test Track", 120, null, albumId, null, java.util.Set.of(genre.getId()));
        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", "audio/mpeg", "test data".getBytes());

        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(albumRepository.findById(albumId)).thenReturn(Optional.of(album));
        when(genreRepository.findAllById(dto.genreIds())).thenReturn(List.of(genre));
        when(trackRepository.existsByFileHash(anyString())).thenReturn(true);

        assertThrows(MusicTrackFileAlreadyExistsException.class, () -> trackService.createTrack(authorId, dto, file));
        verify(minioService, never()).uploadFile(any(), anyString());
        verify(trackRepository, never()).save(any(Track.class));
    }
}

package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.MusicTrackFileAlreadyExistsException;
import com.romanosadchyi.smp.music_app.model.dto.request.TrackCreateDto;
import com.romanosadchyi.smp.music_app.model.entity.Album;
import com.romanosadchyi.smp.music_app.model.entity.Genre;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.repository.GenreRepository;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.service.mapping.TrackMappingService;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.storage.external.FileStorage;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserRole;
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackUploadServiceTest {

    @Mock
    TrackRepository trackRepository;

    @Mock
    UserLookup userLookup;

    @Mock
    AlbumRepository albumRepository;

    @Mock
    GenreRepository genreRepository;

    @Mock
    FileStorage fileStorage;

    @Mock
    TrackMappingService trackMappingService;

    TrackUploadService trackService;

    @BeforeEach
    void setUp() {
        TrackUploadValidator validator = new TrackUploadValidator(userLookup, albumRepository, genreRepository);
        trackService = new TrackUploadService(validator, trackRepository, fileStorage, trackMappingService);
    }

    @Test
    void createTrack_ValidData_UploadsFileAndSavesTrack() throws Exception {
        UUID authorId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        UserSummary author = new UserSummary(authorId, null, null, UserRole.ROLE_ARTIST);
        Album album = Album.builder().id(albumId).authorId(authorId).coverUrl("album-cover.jpg").build();
        Genre genre = Genre.builder().id(UUID.randomUUID()).build();
        
        TrackCreateDto dto = new TrackCreateDto("Test Track", 120, null, albumId, null, Set.of(genre.getId()));
        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", "audio/mpeg", "test data".getBytes());

        when(userLookup.getSummary(authorId)).thenReturn(author);
        when(albumRepository.findById(albumId)).thenReturn(Optional.of(album));
        when(genreRepository.findAllById(dto.genreIds())).thenReturn(List.of(genre));
        when(trackRepository.existsByFileHash(anyString())).thenReturn(false);
        
        Track mappedTrack = Track.builder().build();
        when(trackMappingService.map(dto)).thenReturn(mappedTrack);
        
        Track savedTrack = Track.builder().id(UUID.randomUUID()).coverUrl(null).build();
        when(trackRepository.save(any(Track.class))).thenReturn(savedTrack);

        trackService.createTrack(authorId, dto, file);

        verify(fileStorage).upload(eq(file), anyString());
        verify(trackRepository, times(2)).save(any(Track.class));
    }

    @Test
    void createTrack_DuplicateFileHash_ThrowsConflictException() throws Exception {
        UUID authorId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        UserSummary author = new UserSummary(authorId, null, null, UserRole.ROLE_ARTIST);
        Album album = Album.builder().id(albumId).authorId(authorId).coverUrl("album-cover.jpg").build();
        Genre genre = Genre.builder().id(UUID.randomUUID()).build();
        
        TrackCreateDto dto = new TrackCreateDto("Test Track", 120, null, albumId, null, Set.of(genre.getId()));
        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", "audio/mpeg", "test data".getBytes());

        when(userLookup.getSummary(authorId)).thenReturn(author);
        when(albumRepository.findById(albumId)).thenReturn(Optional.of(album));
        when(genreRepository.findAllById(dto.genreIds())).thenReturn(List.of(genre));
        when(trackRepository.existsByFileHash(anyString())).thenReturn(true);

        assertThrows(MusicTrackFileAlreadyExistsException.class, () -> trackService.createTrack(authorId, dto, file));
        verify(fileStorage, never()).upload(any(), anyString());
    }

    @Test
    void createTrack_UserNotArtist_ThrowsException() {
        UUID authorId = UUID.randomUUID();
        UserSummary author = new UserSummary(authorId, null, null, UserRole.ROLE_USER); // Not an artist
        TrackCreateDto dto = new TrackCreateDto("Test", 120, null, UUID.randomUUID(), null, Set.of());
        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", "audio/mpeg", "test data".getBytes());

        when(userLookup.getSummary(authorId)).thenReturn(author);

        assertThrows(UserNotAuthorizedException.class, () -> trackService.createTrack(authorId, dto, file));
    }
}

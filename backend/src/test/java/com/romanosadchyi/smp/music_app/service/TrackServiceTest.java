package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.MusicTrackFileAlreadyExistsException;
import com.romanosadchyi.smp.music_app.exception.TrackNotFoundException;
import com.romanosadchyi.smp.music_app.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.model.dto.request.TrackCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.RecommendedResponseDto;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
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
    }

    @Test
    void createTrack_UserNotArtist_ThrowsException() {
        UUID authorId = UUID.randomUUID();
        User author = User.builder().id(authorId).role(UserRole.ROLE_USER).build(); // Not an artist
        TrackCreateDto dto = new TrackCreateDto("Test", 120, null, UUID.randomUUID(), null, java.util.Set.of());
        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", "audio/mpeg", "test data".getBytes());

        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));

        assertThrows(UserNotAuthorizedException.class, () -> trackService.createTrack(authorId, dto, file));
    }

    @Test
    void getTrackUrl_Success() throws Exception {
        UUID trackId = UUID.randomUUID();
        Track track = Track.builder().id(trackId).fileKey("key").build();
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));
        when(minioService.generatePresignedUrl("key")).thenReturn("http://url");

        String url = trackService.getTrackUrl(trackId);

        assertEquals("http://url", url);
    }

    @Test
    void likeTrack_Success() {
        UUID userId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        User user = User.builder().id(userId).likedTracks(new HashSet<>()).build();
        Track track = Track.builder().id(trackId).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));

        trackService.likeTrack(userId, trackId);

        assertTrue(user.getLikedTracks().contains(track));
        verify(userRepository).save(user);
    }

    @Test
    void unlikeTrack_Success() {
        UUID userId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        Track track = Track.builder().id(trackId).build();
        User user = User.builder().id(userId).likedTracks(new HashSet<>(List.of(track))).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));

        trackService.unlikeTrack(userId, trackId);

        assertFalse(user.getLikedTracks().contains(track));
        verify(userRepository).save(user);
    }

    @Test
    void getRecommendedTracks_NoLikedTracks_ReturnsNewArrivals() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).likedTracks(new HashSet<>()).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(trackRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        RecommendedResponseDto res = trackService.getRecommendedTracks(userId);

        assertEquals("New Arrivals", res.genreName());
    }
}
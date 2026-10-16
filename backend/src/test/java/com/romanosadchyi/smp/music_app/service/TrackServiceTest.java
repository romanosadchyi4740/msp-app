package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.MusicTrackFileAlreadyExistsException;
import com.romanosadchyi.smp.music_app.exception.TrackNotFoundException;
import com.romanosadchyi.smp.music_app.model.dto.request.TrackCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.RecommendedResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.TrackResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Album;
import com.romanosadchyi.smp.music_app.model.entity.Genre;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.repository.GenreRepository;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.service.mapping.TrackMappingService;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import com.romanosadchyi.smp.music_app.users.external.UserRole;
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
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
import java.util.Set;
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
    UserLookup userLookup;

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

        verify(minioService).uploadFile(eq(file), anyString());
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
        verify(minioService, never()).uploadFile(any(), anyString());
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
        Track track = Track.builder().id(trackId).build();

        when(userLookup.exists(userId)).thenReturn(true);
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));

        trackService.likeTrack(userId, trackId);

        assertTrue(track.getLikedByUserIds().contains(userId));
        verify(trackRepository).save(track);
    }

    @Test
    void unlikeTrack_Success() {
        UUID userId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        Track track = Track.builder().id(trackId).likedByUserIds(new HashSet<>(List.of(userId))).build();

        when(userLookup.exists(userId)).thenReturn(true);
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));

        trackService.unlikeTrack(userId, trackId);

        assertFalse(track.getLikedByUserIds().contains(userId));
        verify(trackRepository).save(track);
    }

    @Test
    void likeTrack_UnknownUser_ThrowsUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userLookup.exists(userId)).thenReturn(false);

        assertThrows(UserNotFoundException.class, () -> trackService.likeTrack(userId, UUID.randomUUID()));
        verifyNoInteractions(trackRepository);
    }

    @Test
    void getLikedTracks_ReturnsTracksLikedByUser() {
        UUID userId = UUID.randomUUID();
        Track track = Track.builder().id(UUID.randomUUID()).build();
        TrackResponseDto dto = TrackResponseDto.builder().id(track.getId()).build();
        when(userLookup.exists(userId)).thenReturn(true);
        when(trackRepository.findAllLikedBy(userId)).thenReturn(List.of(track));
        when(trackMappingService.map(track)).thenReturn(dto);

        assertEquals(List.of(dto), trackService.getLikedTracks(userId));
    }

    @Test
    void getTracksByAuthor_UnknownUser_ThrowsUserNotFound() {
        UUID authorId = UUID.randomUUID();
        when(userLookup.exists(authorId)).thenReturn(false);

        assertThrows(UserNotFoundException.class, () -> trackService.getTracksByAuthor(authorId));
    }

    @Test
    void getRecommendedTracks_NoLikedTracks_ReturnsNewArrivals() {
        UUID userId = UUID.randomUUID();
        when(userLookup.exists(userId)).thenReturn(true);
        when(trackRepository.findAllLikedBy(userId)).thenReturn(List.of());
        when(trackRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        RecommendedResponseDto res = trackService.getRecommendedTracks(userId);

        assertEquals("New Arrivals", res.genreName());
    }

    @Test
    void getAllTracks_ReturnsPage() {
        PageImpl<Track> page = new PageImpl<>(List.of(new Track()));
        when(trackRepository.findAll(any(Pageable.class))).thenReturn(page);
        
        trackService.getAllTracks(0, 10);
        verify(trackRepository).findAll(any(Pageable.class));
    }

    @Test
    void searchTracks_WithAuthorId_ReturnsPage() {
        UUID authorId = UUID.randomUUID();
        PageImpl<Track> page = new PageImpl<>(List.of(new Track()));
        when(trackRepository.findAllByAuthorIdAndTitleContainingIgnoreCase(eq(authorId), eq("query"), any(Pageable.class))).thenReturn(page);
        
        trackService.searchTracks("query", authorId, 0, 10);
        verify(trackRepository).findAllByAuthorIdAndTitleContainingIgnoreCase(eq(authorId), eq("query"), any(Pageable.class));
    }

    @Test
    void searchTracks_WithoutAuthorId_ReturnsPage() {
        PageImpl<Track> page = new PageImpl<>(List.of(new Track()));
        when(trackRepository.findAllByTitleContainingIgnoreCase(eq("query"), any(Pageable.class))).thenReturn(page);
        
        trackService.searchTracks("query", null, 0, 10);
        verify(trackRepository).findAllByTitleContainingIgnoreCase(eq("query"), any(Pageable.class));
    }

    @Test
    void getTracksByGenre_ReturnsPage() {
        UUID genreId = UUID.randomUUID();
        PageImpl<Track> page = new PageImpl<>(List.of(new Track()));
        when(trackRepository.findByGenresId(eq(genreId), any(Pageable.class))).thenReturn(page);
        
        trackService.getTracksByGenre(genreId, 0, 10);
        verify(trackRepository).findByGenresId(eq(genreId), any(Pageable.class));
    }
}

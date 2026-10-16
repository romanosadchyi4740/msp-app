package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.AlbumNotFoundException;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
                .tracks(new ArrayList<>(List.of(track)))
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

    @Test
    void getAlbumById_WhenAlbumExists_ReturnsDto() {
        UUID albumId = UUID.randomUUID();
        Album album = Album.builder().id(albumId).title("Found Album").build();

        when(albumRepository.findById(albumId)).thenReturn(Optional.of(album));
        when(albumMappingService.map(album)).thenReturn(new AlbumResponseDto(albumId, "Found Album", null, null, null, null, null, null));

        AlbumResponseDto response = albumService.getAlbumById(albumId);
        assertNotNull(response);
        assertEquals("Found Album", response.title());
    }

    @Test
    void getAlbumById_WhenAlbumDoesNotExist_ThrowsException() {
        UUID albumId = UUID.randomUUID();
        when(albumRepository.findById(albumId)).thenReturn(Optional.empty());

        assertThrows(AlbumNotFoundException.class, () -> albumService.getAlbumById(albumId));
    }

    @Test
    void getAllAlbums_ReturnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Album> page = new PageImpl<>(List.of(new Album()));

        when(albumRepository.findAll(pageable)).thenReturn(page);
        when(albumMappingService.map(any(Album.class))).thenReturn(new AlbumResponseDto(null, "Title", null, null, null, null, null, null));

        Page<AlbumResponseDto> result = albumService.getAllAlbums(pageable);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getAlbumsByAuthor_ReturnsList() {
        UUID authorId = UUID.randomUUID();
        Album album = Album.builder().id(UUID.randomUUID()).build();
        when(albumRepository.findAllByAuthorId(authorId)).thenReturn(List.of(album));
        when(albumMappingService.map(any(Album.class))).thenReturn(new AlbumResponseDto(null, "Title", null, null, null, null, null, null));

        List<AlbumResponseDto> result = albumService.getAlbumsByAuthor(authorId);
        assertEquals(1, result.size());
    }

    @Test
    void searchAlbums_WithAuthorId_ReturnsPage() {
        UUID authorId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Album> page = new PageImpl<>(List.of(new Album()));

        when(albumRepository.findAllByAuthorIdAndTitleContainingIgnoreCase(authorId, "query", pageable)).thenReturn(page);
        when(albumMappingService.map(any(Album.class))).thenReturn(new AlbumResponseDto(null, "Title", null, null, null, null, null, null));

        Page<AlbumResponseDto> result = albumService.searchAlbums("query", authorId, pageable);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void searchAlbums_WithoutAuthorId_ReturnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Album> page = new PageImpl<>(List.of(new Album()));

        when(albumRepository.findAllByTitleContainingIgnoreCase("query", pageable)).thenReturn(page);
        when(albumMappingService.map(any(Album.class))).thenReturn(new AlbumResponseDto(null, "Title", null, null, null, null, null, null));

        Page<AlbumResponseDto> result = albumService.searchAlbums("query", null, pageable);
        assertEquals(1, result.getTotalElements());
    }
}

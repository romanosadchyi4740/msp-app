package com.romanosadchyi.smp.music_app.music.internal.service;

import com.romanosadchyi.smp.music_app.music.external.AlbumResponseDto;
import com.romanosadchyi.smp.music_app.music.internal.dto.AlbumCreateDto;
import com.romanosadchyi.smp.music_app.music.internal.entity.Album;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.music.internal.exception.AlbumNotFoundException;
import com.romanosadchyi.smp.music_app.music.internal.mapping.AlbumMappingService;
import com.romanosadchyi.smp.music_app.music.internal.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserRole;
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlbumServiceTest {

    @Mock
    AlbumRepository albumRepository;

    @Mock
    UserLookup userLookup;

    @Mock
    TrackRemover trackRemover;

    @Mock
    AlbumMappingService albumMappingService;

    @InjectMocks
    AlbumService albumService;

    @Test
    void createAlbum_WhenUserIsArtist_SavesAlbum() {
        UUID authorId = UUID.randomUUID();
        UserSummary author = new UserSummary(authorId, null, null, UserRole.ROLE_ARTIST);
        AlbumCreateDto dto = new AlbumCreateDto("Test Album", "Desc", "url");

        when(userLookup.getSummary(authorId)).thenReturn(author);
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
        UserSummary author = new UserSummary(authorId, null, null, UserRole.ROLE_USER);
        AlbumCreateDto dto = new AlbumCreateDto("Test Album", "Desc", "url");

        when(userLookup.getSummary(authorId)).thenReturn(author);

        assertThrows(UserNotAuthorizedException.class, () -> albumService.createAlbum(authorId, dto));
        verify(albumRepository, never()).save(any(Album.class));
    }

    @Test
    void deleteAlbum_WhenUserIsOwner_CascadesDeletions() {
        UUID authorId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        
        Track track = Track.builder().id(UUID.randomUUID()).fileKey("file.mp3").build();
        Album album = Album.builder()
                .id(albumId)
                .title("To Delete")
                .authorId(authorId)
                .tracks(new ArrayList<>(List.of(track)))
                .build();

        when(albumRepository.findById(albumId)).thenReturn(Optional.of(album));

        albumService.deleteAlbum(albumId, authorId);

        verify(trackRemover).releaseTrack(track);
        verify(albumRepository).delete(album);
    }

    @Test
    void deleteAlbum_WhenUserIsNotOwner_ThrowsException() {
        UUID authorId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        
        Album album = Album.builder().id(albumId).authorId(authorId).build();

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
        when(albumMappingService.mapPage(page)).thenReturn(page.map(album -> new AlbumResponseDto(null, "Title", null, null, null, null, null, null)));

        Page<AlbumResponseDto> result = albumService.getAllAlbums(pageable);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getAlbumsByAuthor_ReturnsList() {
        UUID authorId = UUID.randomUUID();
        Album album = Album.builder().id(UUID.randomUUID()).build();
        when(albumRepository.findAllByAuthorId(authorId)).thenReturn(List.of(album));
        when(albumMappingService.mapAll(anyList())).thenReturn(List.of(new AlbumResponseDto(null, "Title", null, null, null, null, null, null)));

        List<AlbumResponseDto> result = albumService.getAlbumsByAuthor(authorId);
        assertEquals(1, result.size());
    }

    @Test
    void searchAlbums_WithAuthorId_ReturnsPage() {
        UUID authorId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Album> page = new PageImpl<>(List.of(new Album()));

        when(albumRepository.findAllByAuthorIdAndTitleContainingIgnoreCase(authorId, "query", pageable)).thenReturn(page);
        when(albumMappingService.mapPage(page)).thenReturn(page.map(album -> new AlbumResponseDto(null, "Title", null, null, null, null, null, null)));

        Page<AlbumResponseDto> result = albumService.searchAlbums("query", authorId, pageable);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void searchAlbums_WithoutAuthorId_ReturnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Album> page = new PageImpl<>(List.of(new Album()));

        when(albumRepository.findAllByTitleContainingIgnoreCase("query", pageable)).thenReturn(page);
        when(albumMappingService.mapPage(page)).thenReturn(page.map(album -> new AlbumResponseDto(null, "Title", null, null, null, null, null, null)));

        Page<AlbumResponseDto> result = albumService.searchAlbums("query", null, pageable);
        assertEquals(1, result.getTotalElements());
    }
}

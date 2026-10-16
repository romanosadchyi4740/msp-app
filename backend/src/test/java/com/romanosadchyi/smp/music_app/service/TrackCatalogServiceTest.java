package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.service.mapping.TrackMappingService;
import com.romanosadchyi.smp.music_app.storage.external.FileStorage;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackCatalogServiceTest {

    @Mock
    TrackRepository trackRepository;

    @Mock
    UserLookup userLookup;

    @Mock
    FileStorage fileStorage;

    @Mock
    TrackMappingService trackMappingService;

    @InjectMocks
    TrackCatalogService trackService;

    @Test
    void getTrackUrl_Success() throws Exception {
        UUID trackId = UUID.randomUUID();
        Track track = Track.builder().id(trackId).fileKey("key").build();
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));
        when(fileStorage.presignedGetUrl("key")).thenReturn("http://url");

        String url = trackService.getTrackUrl(trackId);

        assertEquals("http://url", url);
    }

    @Test
    void getTracksByAuthor_UnknownUser_ThrowsUserNotFound() {
        UUID authorId = UUID.randomUUID();
        when(userLookup.exists(authorId)).thenReturn(false);

        assertThrows(UserNotFoundException.class, () -> trackService.getTracksByAuthor(authorId));
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

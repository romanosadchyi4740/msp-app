package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.dto.response.GlobalSearchResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Album;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.service.mapping.AlbumMappingService;
import com.romanosadchyi.smp.music_app.service.mapping.TrackMappingService;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    UserLookup userLookup;

    @Mock
    AlbumRepository albumRepository;

    @Mock
    TrackRepository trackRepository;

    @Mock
    AlbumMappingService albumMappingService;

    @Mock
    TrackMappingService trackMappingService;

    @InjectMocks
    SearchService searchService;

    @Test
    void globalSearch_ReturnsAggregatedResults() {
        String query = "cyber";
        int size = 10;

        Page<UserResponseDto> usersPage = new PageImpl<>(List.of(UserResponseDto.builder().build()));
        Page<Album> albumsPage = new PageImpl<>(List.of(new Album()));
        Page<Track> tracksPage = new PageImpl<>(List.of(new Track()));

        when(userLookup.searchProfiles(eq(query), any(Pageable.class))).thenReturn(usersPage);
        when(albumRepository.findAllByTitleContainingIgnoreCase(eq(query), any(Pageable.class))).thenReturn(albumsPage);
        when(trackRepository.findAllByTitleContainingIgnoreCase(eq(query), any(Pageable.class))).thenReturn(tracksPage);

        GlobalSearchResponseDto response = searchService.globalSearch(query, size);

        assertNotNull(response);
        assertNotNull(response.users());
        assertNotNull(response.albums());
        assertNotNull(response.tracks());

        verify(userLookup).searchProfiles(eq(query), any(Pageable.class));
        verify(albumRepository).findAllByTitleContainingIgnoreCase(eq(query), any(Pageable.class));
        verify(trackRepository).findAllByTitleContainingIgnoreCase(eq(query), any(Pageable.class));
    }
}

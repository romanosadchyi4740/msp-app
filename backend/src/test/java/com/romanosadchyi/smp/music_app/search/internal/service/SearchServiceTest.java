package com.romanosadchyi.smp.music_app.search.internal.service;

import com.romanosadchyi.smp.music_app.music.external.AlbumLookup;
import com.romanosadchyi.smp.music_app.music.external.AlbumResponseDto;
import com.romanosadchyi.smp.music_app.music.external.TrackLookup;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.search.internal.dto.GlobalSearchResponseDto;
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
    TrackLookup trackLookup;

    @Mock
    AlbumLookup albumLookup;

    @InjectMocks
    SearchService searchService;

    @Test
    void globalSearch_ReturnsAggregatedResults() {
        String query = "cyber";
        int size = 10;

        Page<UserResponseDto> usersPage = new PageImpl<>(List.of(UserResponseDto.builder().build()));
        Page<AlbumResponseDto> albumsPage = new PageImpl<>(List.of(new AlbumResponseDto(null, null, null, null, null, null, null, null)));
        Page<TrackResponseDto> tracksPage = new PageImpl<>(List.of(TrackResponseDto.builder().build()));

        when(userLookup.searchProfiles(eq(query), any(Pageable.class))).thenReturn(usersPage);
        when(albumLookup.searchAlbums(eq(query), any(Pageable.class))).thenReturn(albumsPage);
        when(trackLookup.searchTracks(eq(query), any(Pageable.class))).thenReturn(tracksPage);

        GlobalSearchResponseDto response = searchService.globalSearch(query, size);

        assertNotNull(response);
        assertNotNull(response.users());
        assertNotNull(response.albums());
        assertNotNull(response.tracks());

        verify(userLookup).searchProfiles(eq(query), any(Pageable.class));
        verify(albumLookup).searchAlbums(eq(query), any(Pageable.class));
        verify(trackLookup).searchTracks(eq(query), any(Pageable.class));
    }
}

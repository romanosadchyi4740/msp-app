package com.romanosadchyi.smp.music_app.music.internal.mapping;

import com.romanosadchyi.smp.music_app.music.external.GenreResponseDto;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.music.internal.dto.TrackCreateDto;
import com.romanosadchyi.smp.music_app.music.internal.entity.Album;
import com.romanosadchyi.smp.music_app.music.internal.entity.Genre;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserRole;
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackMappingServiceTest {

    @Mock
    GenreMappingService genreMappingService;

    @Mock
    UserLookup userLookup;

    @InjectMocks
    TrackMappingService trackMappingService;

    @Test
    void mapFromDto_ReturnsTrack() {
        TrackCreateDto dto = new TrackCreateDto("Track Title", 120, "cover", null, 1, Set.of());
        
        Track result = trackMappingService.map(dto);
        
        assertNotNull(result);
        assertEquals("Track Title", result.getTitle());
        assertEquals(120, result.getDuration());
        assertEquals("cover", result.getCoverUrl());
        assertEquals(1, result.getTrackNumber());
    }

    @Test
    void mapEntity_ReturnsDto() {
        UUID trackId = UUID.randomUUID();
        UserSummary author = new UserSummary(UUID.randomUUID(), "Author", null, UserRole.ROLE_USER);
        when(userLookup.getSummary(author.id())).thenReturn(author);
        Album album = Album.builder().title("Album").coverUrl("albumCover").build();
        Genre genre = Genre.builder().id(UUID.randomUUID()).build();
        
        Track track = Track.builder()
                .id(trackId)
                .title("Track")
                .authorId(author.id())
                .album(album)
                .duration(200)
                .coverUrl("trackCover")
                .genres(new HashSet<>(List.of(genre)))
                .build();

        when(genreMappingService.map(genre)).thenReturn(GenreResponseDto.builder().id(genre.getId()).build());

        TrackResponseDto result = trackMappingService.map(track);

        assertNotNull(result);
        assertEquals(trackId, result.id());
        assertEquals("Track", result.title());
        assertEquals("Author", result.authorName());
        assertEquals("Album", result.albumTitle());
        assertEquals("trackCover", result.coverUrl());
        assertEquals(1, result.genres().size());
    }

    @Test
    void mapEntity_NullCover_FallsBackToAlbumCover() {
        UserSummary author = new UserSummary(UUID.randomUUID(), "Author", null, UserRole.ROLE_USER);
        when(userLookup.getSummary(author.id())).thenReturn(author);
        Album album = Album.builder().title("Album").coverUrl("albumCover").build();
        
        Track track = Track.builder()
                .id(UUID.randomUUID())
                .title("Track")
                .authorId(author.id())
                .album(album)
                .coverUrl(null)
                .build();

        TrackResponseDto result = trackMappingService.map(track);

        assertEquals("albumCover", result.coverUrl());
    }

    @Test
    void mapEntityWithContext_ReturnsDto() {
        UUID trackId = UUID.randomUUID();
        UserSummary author = new UserSummary(UUID.randomUUID(), "Author", null, UserRole.ROLE_USER);

        Track track = Track.builder()
                .id(trackId)
                .title("Track")
                .authorId(author.id())
                .duration(200)
                .coverUrl(null)
                .build();

        TrackResponseDto result = trackMappingService.map(track, "Context Album", "contextCover", Map.of(author.id(), author));

        assertEquals("contextCover", result.coverUrl());
        assertEquals("Context Album", result.albumTitle());
        assertEquals("Author", result.authorName());
        verifyNoInteractions(userLookup);
    }

    @Test
    void mapAll_LooksUpAllAuthorsInOneCall() {
        UserSummary first = new UserSummary(UUID.randomUUID(), "First", null, UserRole.ROLE_ARTIST);
        UserSummary second = new UserSummary(UUID.randomUUID(), "Second", null, UserRole.ROLE_ARTIST);
        Track a = Track.builder().id(UUID.randomUUID()).authorId(first.id()).build();
        Track b = Track.builder().id(UUID.randomUUID()).authorId(second.id()).build();
        Track c = Track.builder().id(UUID.randomUUID()).authorId(first.id()).build();
        when(userLookup.findSummaries(List.of(first.id(), second.id())))
                .thenReturn(Map.of(first.id(), first, second.id(), second));

        List<TrackResponseDto> result = trackMappingService.mapAll(List.of(a, b, c));

        assertEquals(List.of("First", "Second", "First"), result.stream().map(TrackResponseDto::authorName).toList());
        verify(userLookup, never()).getSummary(any());
    }
}

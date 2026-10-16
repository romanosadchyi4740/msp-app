package com.romanosadchyi.smp.music_app.service.mapping;

import com.romanosadchyi.smp.music_app.model.dto.request.TrackCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.GenreResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.TrackResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Album;
import com.romanosadchyi.smp.music_app.model.entity.Genre;
import com.romanosadchyi.smp.music_app.model.entity.Track;
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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        when(userLookup.getSummary(author.id())).thenReturn(author);
        
        Track track = Track.builder()
                .id(trackId)
                .title("Track")
                .authorId(author.id())
                .duration(200)
                .coverUrl(null)
                .build();

        TrackResponseDto result = trackMappingService.map(track, "Context Album", "contextCover");

        assertEquals("contextCover", result.coverUrl());
        assertEquals("Context Album", result.albumTitle());
    }
}

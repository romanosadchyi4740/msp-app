package com.romanosadchyi.smp.music_app.music.internal.mapping;

import com.romanosadchyi.smp.music_app.music.external.AlbumResponseDto;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.music.internal.entity.Album;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserRole;
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlbumMappingServiceTest {

    @Mock
    TrackMappingService trackMappingService;

    @Mock
    UserLookup userLookup;

    @InjectMocks
    AlbumMappingService albumMappingService;

    @Test
    void map_WithTracks_ReturnsDto() {
        UUID albumId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UserSummary author = new UserSummary(authorId, "Author Name", null, UserRole.ROLE_USER);
        when(userLookup.findSummaries(anyList())).thenReturn(Map.of(author.id(), author));
        Track track = Track.builder().id(UUID.randomUUID()).build();
        Album album = Album.builder()
                .id(albumId)
                .title("Album Title")
                .description("Desc")
                .coverUrl("url")
                .authorId(author.id())
                .releaseDate(LocalDate.now())
                .tracks(List.of(track))
                .build();

        TrackResponseDto mockTrackDto = TrackResponseDto.builder().id(track.getId()).build();
        when(trackMappingService.map(eq(track), eq("Album Title"), eq("url"), anyMap())).thenReturn(mockTrackDto);

        AlbumResponseDto result = albumMappingService.map(album);

        assertNotNull(result);
        assertEquals(albumId, result.id());
        assertEquals("Album Title", result.title());
        assertEquals("Desc", result.description());
        assertEquals("url", result.coverUrl());
        assertEquals(authorId, result.authorId());
        assertEquals("Author Name", result.authorName());
        assertEquals(1, result.tracks().size());
    }

    @Test
    void map_WithoutTracks_ReturnsDtoWithEmptyTracks() {
        UUID albumId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UserSummary author = new UserSummary(authorId, "Author Name", null, UserRole.ROLE_USER);
        when(userLookup.findSummaries(anyList())).thenReturn(Map.of(author.id(), author));
        Album album = Album.builder()
                .id(albumId)
                .title("Album Title")
                .description("Desc")
                .coverUrl("url")
                .authorId(author.id())
                .releaseDate(LocalDate.now())
                .tracks(null) // Null tracks
                .build();

        AlbumResponseDto result = albumMappingService.map(album);

        assertNotNull(result);
        assertEquals(0, result.tracks().size());
    }
}

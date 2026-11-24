package com.romanosadchyi.smp.music_app.playlists.internal.mapping;

import com.romanosadchyi.smp.music_app.music.external.TrackLookup;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.playlists.internal.dto.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.playlists.internal.entity.Playlist;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserRole;
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaylistMappingServiceTest {

    @Mock
    TrackLookup trackLookup;

    @Mock
    UserLookup userLookup;

    @InjectMocks
    PlaylistMappingService playlistMappingService;

    @Test
    void map_NoFork_ReturnsDto() {
        UUID playlistId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();

        Playlist playlist = Playlist.builder()
                .id(playlistId)
                .title("My Playlist")
                .description("Desc")
                .ownerId(ownerId)
                .isPublic(true)
                .coverUrl("url")
                .trackIds(List.of(trackId))
                .build();

        TrackResponseDto trackDto = TrackResponseDto.builder().id(trackId).build();
        when(trackLookup.findTracks(List.of(trackId))).thenReturn(List.of(trackDto));

        PlaylistResponseDto result = playlistMappingService.map(playlist);

        assertNotNull(result);
        assertEquals(playlistId, result.id());
        assertEquals("My Playlist", result.title());
        assertEquals(List.of(trackDto), result.tracks());
        assertEquals(1, result.tracksCount());
        assertNull(result.forkedFromId());
        verifyNoInteractions(userLookup);
    }

    @Test
    void map_WithFork_ReturnsDto() {
        UUID playlistId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        UUID originOwnerId = UUID.randomUUID();
        UserSummary originOwner = new UserSummary(originOwnerId, "Origin Owner", null, UserRole.ROLE_USER);
        Playlist origin = Playlist.builder().id(UUID.randomUUID()).title("Origin Playlist").ownerId(originOwnerId).build();

        Playlist playlist = Playlist.builder()
                .id(playlistId)
                .title("My Playlist")
                .ownerId(ownerId)
                .forkedFrom(origin)
                .build();

        when(userLookup.findSummaries(List.of(originOwnerId))).thenReturn(Map.of(originOwnerId, originOwner));

        PlaylistResponseDto result = playlistMappingService.map(playlist);

        assertNotNull(result);
        assertEquals(0, result.tracksCount());
        assertEquals(origin.getId(), result.forkedFromId());
        assertEquals("Origin Playlist", result.forkedFromName());
        assertEquals("Origin Owner", result.forkedFromOwnerName());
        verifyNoInteractions(trackLookup);
    }

    @Test
    void map_OriginOwnerGone_UsesPlaceholderName() {
        UUID originOwnerId = UUID.randomUUID();
        Playlist origin = Playlist.builder().id(UUID.randomUUID()).ownerId(originOwnerId).build();
        Playlist playlist = Playlist.builder().id(UUID.randomUUID()).forkedFrom(origin).build();
        when(userLookup.findSummaries(List.of(originOwnerId))).thenReturn(Map.of());

        PlaylistResponseDto result = playlistMappingService.map(playlist);

        assertEquals("Unknown Identity", result.forkedFromOwnerName());
    }

    @Test
    void map_TrackMissingFromTheLookup_IsSkipped() {
        UUID kept = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        Playlist playlist = Playlist.builder().id(UUID.randomUUID()).trackIds(List.of(gone, kept)).build();
        TrackResponseDto keptDto = TrackResponseDto.builder().id(kept).build();
        when(trackLookup.findTracks(List.of(gone, kept))).thenReturn(List.of(keptDto));

        PlaylistResponseDto result = playlistMappingService.map(playlist);

        assertEquals(List.of(keptDto), result.tracks());
        assertEquals(1, result.tracksCount());
    }

    @Test
    void mapAll_LooksUpTracksAndOwnersOnceForAllPlaylists() {
        UUID shared = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID originOwnerId = UUID.randomUUID();
        Playlist origin = Playlist.builder().id(UUID.randomUUID()).ownerId(originOwnerId).build();
        Playlist playlist1 = Playlist.builder().id(UUID.randomUUID()).forkedFrom(origin)
                .trackIds(List.of(first, shared, first)).build();
        Playlist playlist2 = Playlist.builder().id(UUID.randomUUID()).forkedFrom(origin)
                .trackIds(List.of(shared, second)).build();
        TrackResponseDto sharedDto = TrackResponseDto.builder().id(shared).build();
        TrackResponseDto firstDto = TrackResponseDto.builder().id(first).build();
        TrackResponseDto secondDto = TrackResponseDto.builder().id(second).build();
        when(trackLookup.findTracks(List.of(first, shared, second))).thenReturn(List.of(firstDto, sharedDto, secondDto));
        when(userLookup.findSummaries(List.of(originOwnerId))).thenReturn(
                Map.of(originOwnerId, new UserSummary(originOwnerId, "Origin Owner", null, UserRole.ROLE_USER)));

        List<PlaylistResponseDto> result = playlistMappingService.mapAll(List.of(playlist1, playlist2));

        assertThat(result).extracting(PlaylistResponseDto::tracks)
                .containsExactly(List.of(firstDto, sharedDto, firstDto), List.of(sharedDto, secondDto));
        assertThat(result).extracting(PlaylistResponseDto::forkedFromOwnerName)
                .containsOnly("Origin Owner");
        verify(trackLookup).findTracks(anyCollection());
        verify(userLookup).findSummaries(anyCollection());
        verify(userLookup, never()).findSummary(any());
        verify(userLookup, never()).getSummary(any());
    }
}

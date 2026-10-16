package com.romanosadchyi.smp.music_app.playlists.internal.mapping;

import com.romanosadchyi.smp.music_app.music.external.TrackLookup;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.playlists.internal.dto.IntegrationRequestResponseDto;
import com.romanosadchyi.smp.music_app.playlists.internal.entity.IntegrationRequest;
import com.romanosadchyi.smp.music_app.playlists.internal.entity.Playlist;
import com.romanosadchyi.smp.music_app.playlists.internal.entity.RequestStatus;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationMappingServiceTest {

    @Mock
    UserLookup userLookup;

    @Mock
    TrackLookup trackLookup;

    @InjectMocks
    IntegrationMappingService integrationMappingService;

    @Test
    void map_ReturnsDto() {
        UUID requestId = UUID.randomUUID();
        UUID playlistId = UUID.randomUUID();
        Playlist targetPlaylist = Playlist.builder().id(playlistId).build();
        UUID requesterId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        IntegrationRequest request = IntegrationRequest.builder()
                .id(requestId)
                .targetPlaylist(targetPlaylist)
                .requesterId(requesterId)
                .proposedTrackIds(new LinkedHashSet<>(List.of(trackId)))
                .status(RequestStatus.PENDING)
                .createdAt(now)
                .build();

        UserResponseDto requesterDto = UserResponseDto.builder().id(requesterId).build();
        TrackResponseDto trackDto = TrackResponseDto.builder().id(trackId).build();

        when(userLookup.getProfile(requesterId)).thenReturn(requesterDto);
        when(trackLookup.findTracks(List.of(trackId))).thenReturn(List.of(trackDto));

        IntegrationRequestResponseDto result = integrationMappingService.map(request);

        assertNotNull(result);
        assertEquals(requestId, result.id());
        assertEquals(playlistId, result.targetPlaylistId());
        assertEquals(requesterDto, result.requester());
        assertEquals(List.of(trackDto), result.proposedTracks());
        assertEquals("PENDING", result.status());
        assertEquals(now, result.createdAt());
    }

    @Test
    void mapAll_LooksUpTracksOnceForAllRequests() {
        Playlist target = Playlist.builder().id(UUID.randomUUID()).build();
        UUID shared = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        IntegrationRequest first = request(target, shared);
        IntegrationRequest second = request(target, other, shared);
        TrackResponseDto sharedDto = TrackResponseDto.builder().id(shared).build();
        TrackResponseDto otherDto = TrackResponseDto.builder().id(other).build();
        when(trackLookup.findTracks(List.of(shared, other))).thenReturn(List.of(sharedDto, otherDto));

        List<IntegrationRequestResponseDto> result = integrationMappingService.mapAll(List.of(first, second));

        assertEquals(List.of(sharedDto), result.get(0).proposedTracks());
        assertEquals(List.of(otherDto, sharedDto), result.get(1).proposedTracks());
        verify(trackLookup).findTracks(anyCollection());
    }

    private static IntegrationRequest request(Playlist target, UUID... trackIds) {
        return IntegrationRequest.builder()
                .id(UUID.randomUUID())
                .targetPlaylist(target)
                .requesterId(UUID.randomUUID())
                .proposedTrackIds(new LinkedHashSet<>(List.of(trackIds)))
                .status(RequestStatus.PENDING)
                .build();
    }
}

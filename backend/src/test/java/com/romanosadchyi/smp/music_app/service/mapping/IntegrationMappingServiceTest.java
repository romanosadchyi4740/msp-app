package com.romanosadchyi.smp.music_app.service.mapping;

import com.romanosadchyi.smp.music_app.model.dto.response.IntegrationRequestResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.IntegrationRequest;
import com.romanosadchyi.smp.music_app.model.entity.Playlist;
import com.romanosadchyi.smp.music_app.model.enums.RequestStatus;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.music.internal.mapping.TrackMappingService;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationMappingServiceTest {

    @Mock
    UserLookup userLookup;

    @Mock
    TrackMappingService trackMappingService;

    @InjectMocks
    IntegrationMappingService integrationMappingService;

    @Test
    void map_ReturnsDto() {
        UUID requestId = UUID.randomUUID();
        UUID playlistId = UUID.randomUUID();
        Playlist targetPlaylist = Playlist.builder().id(playlistId).build();
        UUID requesterId = UUID.randomUUID();
        Track track = Track.builder().id(UUID.randomUUID()).build();
        LocalDateTime now = LocalDateTime.now();

        IntegrationRequest request = IntegrationRequest.builder()
                .id(requestId)
                .targetPlaylist(targetPlaylist)
                .requesterId(requesterId)
                .proposedTracks(new HashSet<>(List.of(track)))
                .status(RequestStatus.PENDING)
                .createdAt(now)
                .build();

        UserResponseDto requesterDto = UserResponseDto.builder().id(requesterId).build();
        TrackResponseDto trackDto = TrackResponseDto.builder().id(track.getId()).build();

        when(userLookup.getProfile(requesterId)).thenReturn(requesterDto);
        when(trackMappingService.mapAll(anyCollection())).thenReturn(List.of(trackDto));

        IntegrationRequestResponseDto result = integrationMappingService.map(request);

        assertNotNull(result);
        assertEquals(requestId, result.id());
        assertEquals(playlistId, result.targetPlaylistId());
        assertEquals(requesterDto, result.requester());
        assertEquals(1, result.proposedTracks().size());
        assertEquals("PENDING", result.status());
        assertEquals(now, result.createdAt());
    }
}

package com.romanosadchyi.smp.music_app.service.mapping;

import com.romanosadchyi.smp.music_app.model.dto.response.IntegrationRequestResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.TrackResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.UserResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.IntegrationRequest;
import com.romanosadchyi.smp.music_app.model.entity.Playlist;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.enums.RequestStatus;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationMappingServiceTest {

    @Mock
    UserMappingService userMappingService;

    @Mock
    TrackMappingService trackMappingService;

    @InjectMocks
    IntegrationMappingService integrationMappingService;

    @Test
    void map_ReturnsDto() {
        UUID requestId = UUID.randomUUID();
        UUID playlistId = UUID.randomUUID();
        Playlist targetPlaylist = Playlist.builder().id(playlistId).build();
        User requester = User.builder().id(UUID.randomUUID()).build();
        Track track = Track.builder().id(UUID.randomUUID()).build();
        LocalDateTime now = LocalDateTime.now();

        IntegrationRequest request = IntegrationRequest.builder()
                .id(requestId)
                .targetPlaylist(targetPlaylist)
                .requester(requester)
                .proposedTracks(new HashSet<>(List.of(track)))
                .status(RequestStatus.PENDING)
                .createdAt(now)
                .build();

        UserResponseDto requesterDto = UserResponseDto.builder().id(requester.getId()).build();
        TrackResponseDto trackDto = TrackResponseDto.builder().id(track.getId()).build();

        when(userMappingService.map(requester)).thenReturn(requesterDto);
        when(trackMappingService.map(track)).thenReturn(trackDto);

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

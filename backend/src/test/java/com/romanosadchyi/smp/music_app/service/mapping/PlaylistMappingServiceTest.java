package com.romanosadchyi.smp.music_app.service.mapping;

import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.TrackResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Playlist;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaylistMappingServiceTest {

    @Mock
    TrackMappingService trackMappingService;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    PlaylistMappingService playlistMappingService;

    @Test
    void map_NoFork_ReturnsDto() {
        UUID playlistId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Track track = Track.builder().id(UUID.randomUUID()).build();
        
        Playlist playlist = Playlist.builder()
                .id(playlistId)
                .title("My Playlist")
                .description("Desc")
                .ownerId(ownerId)
                .isPublic(true)
                .coverUrl("url")
                .tracks(List.of(track))
                .build();

        TrackResponseDto trackDto = TrackResponseDto.builder().id(track.getId()).build();
        when(trackMappingService.map(track)).thenReturn(trackDto);

        PlaylistResponseDto result = playlistMappingService.map(playlist);

        assertNotNull(result);
        assertEquals(playlistId, result.id());
        assertEquals("My Playlist", result.title());
        assertEquals(1, result.tracksCount());
        assertNull(result.forkedFromId());
    }

    @Test
    void map_WithFork_ReturnsDto() {
        UUID playlistId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        
        UUID originOwnerId = UUID.randomUUID();
        User originOwner = User.builder().id(originOwnerId).displayName("Origin Owner").build();
        Playlist origin = Playlist.builder().id(UUID.randomUUID()).title("Origin Playlist").ownerId(originOwnerId).build();
        
        Playlist playlist = Playlist.builder()
                .id(playlistId)
                .title("My Playlist")
                .ownerId(ownerId)
                .forkedFrom(origin)
                .tracks(null)
                .build();

        when(userRepository.findById(originOwnerId)).thenReturn(Optional.of(originOwner));

        PlaylistResponseDto result = playlistMappingService.map(playlist);

        assertNotNull(result);
        assertEquals(0, result.tracksCount());
        assertEquals(origin.getId(), result.forkedFromId());
        assertEquals("Origin Playlist", result.forkedFromName());
        assertEquals("Origin Owner", result.forkedFromOwnerName());
    }
}

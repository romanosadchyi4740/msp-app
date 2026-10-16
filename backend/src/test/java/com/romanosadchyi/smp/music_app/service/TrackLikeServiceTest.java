package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.dto.response.TrackResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.service.mapping.TrackMappingService;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackLikeServiceTest {

    @Mock
    TrackRepository trackRepository;

    @Mock
    UserLookup userLookup;

    @Mock
    TrackMappingService trackMappingService;

    @InjectMocks
    TrackLikeService trackService;

    @Test
    void likeTrack_Success() {
        UUID userId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        Track track = Track.builder().id(trackId).build();

        when(userLookup.exists(userId)).thenReturn(true);
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));

        trackService.likeTrack(userId, trackId);

        assertTrue(track.getLikedByUserIds().contains(userId));
        verify(trackRepository).save(track);
    }

    @Test
    void unlikeTrack_Success() {
        UUID userId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        Track track = Track.builder().id(trackId).likedByUserIds(new HashSet<>(List.of(userId))).build();

        when(userLookup.exists(userId)).thenReturn(true);
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));

        trackService.unlikeTrack(userId, trackId);

        assertFalse(track.getLikedByUserIds().contains(userId));
        verify(trackRepository).save(track);
    }

    @Test
    void likeTrack_UnknownUser_ThrowsUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userLookup.exists(userId)).thenReturn(false);

        assertThrows(UserNotFoundException.class, () -> trackService.likeTrack(userId, UUID.randomUUID()));
        verifyNoInteractions(trackRepository);
    }

    @Test
    void getLikedTracks_ReturnsTracksLikedByUser() {
        UUID userId = UUID.randomUUID();
        Track track = Track.builder().id(UUID.randomUUID()).build();
        TrackResponseDto dto = TrackResponseDto.builder().id(track.getId()).build();
        when(userLookup.exists(userId)).thenReturn(true);
        when(trackRepository.findAllLikedBy(userId)).thenReturn(List.of(track));
        when(trackMappingService.map(track)).thenReturn(dto);

        assertEquals(List.of(dto), trackService.getLikedTracks(userId));
    }
}

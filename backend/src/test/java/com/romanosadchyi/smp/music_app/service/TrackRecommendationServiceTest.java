package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.dto.response.RecommendedResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.TrackResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Genre;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.service.mapping.TrackMappingService;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackRecommendationServiceTest {

    @Mock
    TrackRepository trackRepository;

    @Mock
    UserLookup userLookup;

    @Mock
    TrackMappingService trackMappingService;

    @InjectMocks
    TrackRecommendationService trackService;

    @Test
    void getRecommendedTracks_NoLikedTracks_ReturnsNewArrivals() {
        UUID userId = UUID.randomUUID();
        when(userLookup.exists(userId)).thenReturn(true);
        when(trackRepository.findAllLikedBy(userId)).thenReturn(List.of());
        when(trackRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        RecommendedResponseDto res = trackService.getRecommendedTracks(userId);

        assertEquals("New Arrivals", res.genreName());
    }

    @Test
    void getRecommendedTracks_LikedGenre_RecommendsUnlikedTracksOfTopGenre() {
        UUID userId = UUID.randomUUID();
        Genre rock = Genre.builder().id(UUID.randomUUID()).name("Rock").build();
        Genre jazz = Genre.builder().id(UUID.randomUUID()).name("Jazz").build();
        Track likedRock1 = Track.builder().id(UUID.randomUUID()).genres(Set.of(rock)).build();
        Track likedRock2 = Track.builder().id(UUID.randomUUID()).genres(Set.of(rock, jazz)).build();
        Track recommended = Track.builder().id(UUID.randomUUID()).genres(Set.of(rock)).build();
        TrackResponseDto recommendedDto = TrackResponseDto.builder().id(recommended.getId()).build();
        when(userLookup.exists(userId)).thenReturn(true);
        when(trackRepository.findAllLikedBy(userId)).thenReturn(List.of(likedRock1, likedRock2));
        when(trackRepository.findByGenresIdAndIdNotIn(eq(rock.getId()), eq(List.of(likedRock1.getId(), likedRock2.getId())), any(Pageable.class)))
                .thenReturn(List.of(recommended));
        when(trackMappingService.map(recommended)).thenReturn(recommendedDto);

        RecommendedResponseDto res = trackService.getRecommendedTracks(userId);

        assertEquals("Rock", res.genreName());
        assertEquals(List.of(recommendedDto), res.tracks());
    }

    @Test
    void getRecommendedTracks_AllOfTopGenreLiked_FallsBackToTopGenre() {
        UUID userId = UUID.randomUUID();
        Genre rock = Genre.builder().id(UUID.randomUUID()).name("Rock").build();
        Track liked = Track.builder().id(UUID.randomUUID()).genres(Set.of(rock)).build();
        when(userLookup.exists(userId)).thenReturn(true);
        when(trackRepository.findAllLikedBy(userId)).thenReturn(List.of(liked));
        when(trackRepository.findByGenresIdAndIdNotIn(eq(rock.getId()), any(), any(Pageable.class))).thenReturn(List.of());
        when(trackRepository.findByGenresId(eq(rock.getId()), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(liked)));

        RecommendedResponseDto res = trackService.getRecommendedTracks(userId);

        assertEquals("Rock", res.genreName());
        assertEquals(1, res.tracks().size());
    }
}

package com.romanosadchyi.smp.music_app.music.internal.service;

import com.romanosadchyi.smp.music_app.music.internal.dto.RecommendedResponseDto;
import com.romanosadchyi.smp.music_app.music.internal.entity.Genre;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.music.internal.mapping.TrackMappingService;
import com.romanosadchyi.smp.music_app.music.internal.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Recommendations from the genre a user likes most, falling back to the newest tracks.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TrackRecommendationService {

    TrackRepository trackRepository;
    UserLookup userLookup;
    TrackMappingService trackMappingService;

    static final String NEW_ARRIVALS = "New Arrivals";
    static final Pageable NEWEST_TEN = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

    @Transactional(readOnly = true)
    public RecommendedResponseDto getRecommendedTracks(UUID userId) {
        requireUser(userId);

        List<Track> likedTracks = trackRepository.findAllLikedBy(userId);
        Genre topGenre = likedTracks.stream()
                .flatMap(track -> track.getGenres().stream())
                .collect(Collectors.groupingBy(genre -> genre, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        // Nothing liked yet, or only tracks without genres
        if (topGenre == null) {
            return new RecommendedResponseDto(NEW_ARRIVALS, trackMappingService.mapAll(trackRepository.findAll(NEWEST_TEN).getContent()));
        }

        List<UUID> likedTrackIds = likedTracks.stream().map(Track::getId).toList();
        List<Track> recommended = trackRepository.findByGenresIdAndIdNotIn(topGenre.getId(), likedTrackIds, NEWEST_TEN);
        if (recommended.isEmpty()) {
            recommended = trackRepository.findByGenresId(topGenre.getId(), NEWEST_TEN).getContent();
        }

        return new RecommendedResponseDto(topGenre.getName(), trackMappingService.mapAll(recommended));
    }

    private void requireUser(UUID userId) {
        if (!userLookup.exists(userId)) {
            throw new UserNotFoundException("User not found with id: " + userId);
        }
    }
}

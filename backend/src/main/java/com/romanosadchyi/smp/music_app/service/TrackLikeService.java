package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.TrackNotFoundException;
import com.romanosadchyi.smp.music_app.model.dto.response.TrackResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.service.mapping.TrackMappingService;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Users liking tracks and their list of liked tracks.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TrackLikeService {

    TrackRepository trackRepository;
    UserLookup userLookup;
    TrackMappingService trackMappingService;

    @Transactional
    public void likeTrack(UUID userId, UUID trackId) {
        requireUser(userId);
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new TrackNotFoundException("Track not found"));

        track.getLikedByUserIds().add(userId);
        trackRepository.save(track);
    }

    @Transactional
    public void unlikeTrack(UUID userId, UUID trackId) {
        requireUser(userId);
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new TrackNotFoundException("Track not found"));

        track.getLikedByUserIds().remove(userId);
        trackRepository.save(track);
    }

    @Transactional(readOnly = true)
    public List<TrackResponseDto> getLikedTracks(UUID userId) {
        requireUser(userId);

        return trackRepository.findAllLikedBy(userId).stream()
                .map(trackMappingService::map)
                .toList();
    }

    private void requireUser(UUID userId) {
        if (!userLookup.exists(userId)) {
            throw new UserNotFoundException("User not found with id: " + userId);
        }
    }
}

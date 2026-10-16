package com.romanosadchyi.smp.music_app.music.internal.service;

import com.romanosadchyi.smp.music_app.music.external.AlbumLookup;
import com.romanosadchyi.smp.music_app.music.external.AlbumResponseDto;
import com.romanosadchyi.smp.music_app.music.external.TrackLookup;
import com.romanosadchyi.smp.music_app.music.external.TrackNotFoundException;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.music.internal.mapping.AlbumMappingService;
import com.romanosadchyi.smp.music_app.music.internal.mapping.TrackMappingService;
import com.romanosadchyi.smp.music_app.music.internal.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.music.internal.repository.TrackRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class MusicLookupService implements TrackLookup, AlbumLookup {

    TrackRepository trackRepository;
    AlbumRepository albumRepository;
    TrackMappingService trackMappingService;
    AlbumMappingService albumMappingService;

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID trackId) {
        return trackRepository.existsById(trackId);
    }

    @Override
    @Transactional(readOnly = true)
    public TrackResponseDto getTrack(UUID trackId) {
        return trackMappingService.map(trackRepository.findById(trackId)
                .orElseThrow(() -> new TrackNotFoundException("Track not found")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrackResponseDto> findTracks(Collection<UUID> trackIds) {
        Map<UUID, Track> byId = trackRepository.findAllById(trackIds).stream()
                .collect(Collectors.toMap(Track::getId, Function.identity()));
        return trackMappingService.mapAll(trackIds.stream().map(byId::get).filter(Objects::nonNull).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TrackResponseDto> searchTracks(String titleQuery, Pageable pageable) {
        return trackMappingService.mapPage(trackRepository.findAllByTitleContainingIgnoreCase(titleQuery, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AlbumResponseDto> searchAlbums(String titleQuery, Pageable pageable) {
        return albumMappingService.mapPage(albumRepository.findAllByTitleContainingIgnoreCase(titleQuery, pageable));
    }
}

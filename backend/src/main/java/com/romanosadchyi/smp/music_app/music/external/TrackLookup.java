package com.romanosadchyi.smp.music_app.music.external;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Read access to tracks for other modules, which store only track ids.
 */
public interface TrackLookup {

    boolean exists(UUID trackId);

    /**
     * @throws TrackNotFoundException if there is no such track
     */
    TrackResponseDto getTrack(UUID trackId);

    /**
     * The tracks in the order of {@code trackIds}; ids without a track are skipped.
     */
    List<TrackResponseDto> findTracks(Collection<UUID> trackIds);

    Page<TrackResponseDto> searchTracks(String titleQuery, Pageable pageable);
}

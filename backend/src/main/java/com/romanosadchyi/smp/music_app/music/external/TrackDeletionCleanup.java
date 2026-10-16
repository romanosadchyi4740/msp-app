package com.romanosadchyi.smp.music_app.music.external;

import java.util.UUID;

/**
 * Implemented by every module that references tracks (playlists, chat messages). Deleting a track calls all
 * implementations in the same transaction, before the track row is removed, so {@code music} needs no knowledge
 * of who refers to its tracks.
 */
public interface TrackDeletionCleanup {

    void deleteTrackReferences(UUID trackId);
}

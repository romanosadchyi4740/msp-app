CREATE TABLE integration_requests (
    id UUID PRIMARY KEY,
    target_playlist_id UUID NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
    requester_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE integration_request_tracks (
    request_id UUID NOT NULL REFERENCES integration_requests(id) ON DELETE CASCADE,
    track_id UUID NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
    PRIMARY KEY (request_id, track_id)
);
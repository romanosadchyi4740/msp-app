CREATE TABLE IF NOT EXISTS user_liked_tracks (
    user_id UUID NOT NULL,
    track_id UUID NOT NULL,
    PRIMARY KEY (user_id, track_id),
    CONSTRAINT fk_user_liked_tracks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_liked_tracks_track FOREIGN KEY (track_id) REFERENCES tracks (id) ON DELETE CASCADE
);

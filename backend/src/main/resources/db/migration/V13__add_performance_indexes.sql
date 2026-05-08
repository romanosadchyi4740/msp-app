-- users
CREATE INDEX idx_users_display_name ON users(display_name);

-- tracks
CREATE INDEX idx_tracks_title ON tracks(title);
CREATE INDEX idx_tracks_author_id ON tracks(author_id);

-- playlists
CREATE INDEX idx_playlists_title ON playlists(title);
CREATE INDEX idx_playlists_owner_id ON playlists(owner_id);

-- chat_memberships
CREATE INDEX idx_chat_memberships_user_status ON chat_memberships(user_id, status);

-- messages
CREATE INDEX idx_messages_chat_id_timestamp ON messages(chat_id, timestamp DESC);

-- join tables reverse lookups
CREATE INDEX idx_user_liked_tracks_track_id ON user_liked_tracks(track_id);
CREATE INDEX idx_user_followers_following_id ON user_followers(following_id);
CREATE INDEX idx_track_genres_genre_id ON track_genres(genre_id);
CREATE INDEX idx_playlist_tracks_track_id ON playlist_tracks(track_id);
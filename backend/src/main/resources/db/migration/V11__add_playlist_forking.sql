ALTER TABLE playlists ADD COLUMN forked_from_id UUID;
ALTER TABLE playlists ADD CONSTRAINT fk_playlist_forked_from FOREIGN KEY (forked_from_id) REFERENCES playlists (id) ON DELETE SET NULL;

-- Foreign keys without an index; track deletion updates messages and broadcasts by track id
CREATE INDEX idx_messages_sender_id ON messages(sender_id);
CREATE INDEX idx_messages_shared_track_id ON messages(shared_track_id);
CREATE INDEX idx_status_broadcasts_attached_track_id ON status_broadcasts(attached_track_id);

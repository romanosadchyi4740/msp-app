-- Room text chat (KAN-51, FR-12; lab 7 ChatMessage). The author is a user, not a room_participants row, because a
-- participant who leaves loses that row while their messages stay in the room's history. Content is AES-256-GCM
-- ciphertext ("v2:" + base64), written by the application's converter, never plain text
CREATE TABLE room_messages (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    author_id UUID NOT NULL,
    content TEXT NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_room_messages_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_room_messages_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
);

-- History pages of a room, newest first
CREATE INDEX idx_room_messages_room_sent ON room_messages(room_id, sent_at);
-- Account deletion removes the author's messages
CREATE INDEX idx_room_messages_author ON room_messages(author_id);

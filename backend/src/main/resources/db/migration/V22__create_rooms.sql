-- Rooms (lab 7: Room, RoomParticipant); the host is also a participant with role HOST
CREATE TABLE rooms (
    id UUID PRIMARY KEY,
    host_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    invite_code VARCHAR(32) NOT NULL,
    -- Host and up to 4 participants, which also caps the WebRTC mesh at 4 peers (FR-5)
    capacity INTEGER NOT NULL DEFAULT 5,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_rooms_host FOREIGN KEY (host_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_rooms_invite_code UNIQUE (invite_code),
    CONSTRAINT chk_rooms_capacity CHECK (capacity BETWEEN 1 AND 5),
    CONSTRAINT chk_rooms_status CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE INDEX idx_rooms_host ON rooms(host_id);

CREATE TABLE room_participants (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    rtt_ms INTEGER,
    online BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_room_participants_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_room_participants_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_room_participants_room_user UNIQUE (room_id, user_id),
    CONSTRAINT chk_room_participants_role CHECK (role IN ('HOST', 'PARTICIPANT')),
    CONSTRAINT chk_room_participants_rtt CHECK (rtt_ms >= 0)
);

CREATE INDEX idx_room_participants_user ON room_participants(user_id);
-- One host per room
CREATE UNIQUE INDEX uq_room_participants_host ON room_participants(room_id) WHERE role = 'HOST';

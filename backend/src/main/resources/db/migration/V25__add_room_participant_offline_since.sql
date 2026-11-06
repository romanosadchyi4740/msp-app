-- When a participant last went offline (KAN-36); NULL while online or never seen online. A host offline for more
-- than 60 seconds hands the role over.
ALTER TABLE room_participants ADD COLUMN offline_since TIMESTAMPTZ;

-- The away-host check every few seconds looks up offline times
CREATE INDEX idx_room_participants_offline_since ON room_participants(offline_since);

CREATE TABLE IF NOT EXISTS status_broadcasts (
    id UUID PRIMARY KEY,
    author_id UUID NOT NULL,
    content TEXT NOT NULL,
    attached_track_id UUID,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_status_broadcasts_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_status_broadcasts_track FOREIGN KEY (attached_track_id) REFERENCES tracks(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS consensus_polls (
    id UUID PRIMARY KEY,
    broadcast_id UUID NOT NULL UNIQUE,
    question VARCHAR(255) NOT NULL,
    CONSTRAINT fk_consensus_polls_broadcast FOREIGN KEY (broadcast_id) REFERENCES status_broadcasts(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS poll_options (
    id UUID PRIMARY KEY,
    poll_id UUID NOT NULL,
    option_text VARCHAR(255) NOT NULL,
    CONSTRAINT fk_poll_options_poll FOREIGN KEY (poll_id) REFERENCES consensus_polls(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS poll_votes (
    id UUID PRIMARY KEY,
    option_id UUID NOT NULL,
    voter_id UUID NOT NULL,
    CONSTRAINT fk_poll_votes_option FOREIGN KEY (option_id) REFERENCES poll_options(id) ON DELETE CASCADE,
    CONSTRAINT fk_poll_votes_voter FOREIGN KEY (voter_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_poll_votes_voter_poll UNIQUE(voter_id, option_id) -- Will enforce poll-level uniqueness via service
);

CREATE INDEX idx_status_broadcasts_author ON status_broadcasts(author_id);
CREATE INDEX idx_status_broadcasts_timestamp ON status_broadcasts(timestamp DESC);

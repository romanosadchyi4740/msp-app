-- Tabs (lab 7: Tab, GuitarProTab, AsciiTab) in one table; format is the JPA discriminator
CREATE TABLE tabs (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    format VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    artist VARCHAR(255),
    -- Guitar Pro file in object storage
    file_key VARCHAR(255),
    gp_version VARCHAR(20),
    -- ASCII tab text
    raw_text TEXT,
    tempo_bpm INTEGER,
    time_signature VARCHAR(10),
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    rights_confirmed BOOLEAN NOT NULL,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_tabs_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_tabs_format CHECK (format IN ('GUITAR_PRO', 'ASCII')),
    -- Each format has its own content and none of the other's
    CONSTRAINT chk_tabs_content CHECK (
        (format = 'GUITAR_PRO' AND file_key IS NOT NULL AND raw_text IS NULL)
        OR (format = 'ASCII' AND raw_text IS NOT NULL AND file_key IS NULL AND gp_version IS NULL)),
    CONSTRAINT chk_tabs_tempo CHECK (tempo_bpm > 0),
    -- Only material the uploader owns or that is in the public domain (R14)
    CONSTRAINT chk_tabs_rights_confirmed CHECK (rights_confirmed)
);

CREATE INDEX idx_tabs_owner ON tabs(owner_id);
CREATE INDEX idx_tabs_title_artist ON tabs(title, artist);

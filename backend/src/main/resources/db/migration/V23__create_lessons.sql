-- Lessons in rooms (lab 7: Session as lesson_sessions, PlaybackState); KAN-35
CREATE TABLE lesson_sessions (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    -- The tab on screen; a deleted tab leaves the lesson without one
    tab_id UUID,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    -- 40 for the free plan, NULL for no limit (Premium)
    limit_minutes INTEGER,
    limit_warning_sent BOOLEAN NOT NULL DEFAULT FALSE,
    end_reason VARCHAR(20),
    CONSTRAINT fk_lesson_sessions_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_lesson_sessions_tab FOREIGN KEY (tab_id) REFERENCES tabs(id) ON DELETE SET NULL,
    CONSTRAINT chk_lesson_sessions_limit CHECK (limit_minutes > 0),
    CONSTRAINT chk_lesson_sessions_end_reason CHECK (end_reason IN ('HOST_ENDED', 'LIMIT_REACHED', 'ROOM_CLOSED')),
    CONSTRAINT chk_lesson_sessions_ended CHECK ((ended_at IS NULL) = (end_reason IS NULL))
);

CREATE INDEX idx_lesson_sessions_room ON lesson_sessions(room_id);
CREATE INDEX idx_lesson_sessions_tab ON lesson_sessions(tab_id);
-- One active lesson per room
CREATE UNIQUE INDEX uq_lesson_sessions_active_room ON lesson_sessions(room_id) WHERE ended_at IS NULL;
-- Active lessons with a limit, checked every minute
CREATE INDEX idx_lesson_sessions_active_limited ON lesson_sessions(started_at)
    WHERE ended_at IS NULL AND limit_minutes IS NOT NULL;

-- The shared position of a lesson (anchor-time model); one row per lesson
CREATE TABLE playback_states (
    session_id UUID PRIMARY KEY,
    measure INTEGER NOT NULL,
    beat DOUBLE PRECISION NOT NULL,
    tempo_bpm INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    -- Server epoch milliseconds at which (measure, beat) holds
    anchor_time BIGINT NOT NULL,
    metronome_on BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL,
    CONSTRAINT fk_playback_states_session FOREIGN KEY (session_id) REFERENCES lesson_sessions(id) ON DELETE CASCADE,
    CONSTRAINT chk_playback_states_measure CHECK (measure >= 1),
    CONSTRAINT chk_playback_states_beat CHECK (beat >= 0),
    CONSTRAINT chk_playback_states_tempo CHECK (tempo_bpm > 0),
    CONSTRAINT chk_playback_states_status CHECK (status IN ('PLAYING', 'PAUSED', 'STOPPED'))
);

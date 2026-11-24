-- The time signature of the lesson's tab, so positions wrap into measures (KAN-39); existing rows become 4/4
ALTER TABLE playback_states
    ADD COLUMN beats_per_measure INTEGER NOT NULL DEFAULT 4,
    ADD COLUMN beat_unit INTEGER NOT NULL DEFAULT 4,
    ADD CONSTRAINT chk_playback_states_beats_per_measure CHECK (beats_per_measure BETWEEN 1 AND 99),
    ADD CONSTRAINT chk_playback_states_beat_unit CHECK (beat_unit IN (1, 2, 4, 8, 16, 32));

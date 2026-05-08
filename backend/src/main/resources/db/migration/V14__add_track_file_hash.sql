ALTER TABLE tracks ADD COLUMN file_hash VARCHAR(255);
ALTER TABLE tracks ADD CONSTRAINT unique_file_hash UNIQUE (file_hash);

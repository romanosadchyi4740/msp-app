ALTER TABLE users ADD COLUMN biography TEXT;

ALTER TABLE albums DROP CONSTRAINT author_id_fk;
ALTER TABLE albums ADD CONSTRAINT author_id_fk FOREIGN KEY (author_id) REFERENCES users(id);

ALTER TABLE tracks DROP CONSTRAINT author_id_fk;
ALTER TABLE tracks ADD CONSTRAINT author_id_fk FOREIGN KEY (author_id) REFERENCES users(id);

DROP TABLE IF EXISTS authors;

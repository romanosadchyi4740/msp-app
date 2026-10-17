-- A chat attachment belongs to the one message that carries it: download URLs are given to readers of that
-- message's chat, so the key must not be re-posted elsewhere. Only crafted messages could have repeated a key;
-- those later copies lose it before the unique index is built.
UPDATE messages m
SET file_url = NULL
WHERE m.file_url IS NOT NULL
  AND EXISTS (SELECT 1 FROM messages e
              WHERE e.file_url = m.file_url
                AND (e.timestamp < m.timestamp OR (e.timestamp = m.timestamp AND e.id < m.id)));

CREATE UNIQUE INDEX idx_messages_file_url ON messages(file_url) WHERE file_url IS NOT NULL;

ALTER TABLE chats ADD COLUMN is_group BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE chats ADD COLUMN name VARCHAR(255);

CREATE TABLE chat_memberships (
    id UUID PRIMARY KEY,
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    is_admin BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(chat_id, user_id)
);

INSERT INTO chat_memberships (id, chat_id, user_id, status, is_admin)
SELECT
    gen_random_uuid(),
    cp.chat_id,
    cp.user_id,
    COALESCE(c.status, 'PENDING'),
    CASE WHEN c.initiator_id = cp.user_id THEN TRUE ELSE FALSE END
FROM chat_participants cp
JOIN chats c ON c.id = cp.chat_id;

ALTER TABLE chats DROP CONSTRAINT IF EXISTS fk_chat_initiator;
ALTER TABLE chats DROP COLUMN initiator_id;
ALTER TABLE chats DROP COLUMN status;
DROP TABLE chat_participants;

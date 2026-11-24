-- Set when an account is deleted; the row itself is only anonymized, so other modules' rows can keep pointing at it
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;

-- Accounts deleted before this migration, recognised by the address account deletion gives them
UPDATE users SET deleted_at = COALESCE(updated_at, now()) WHERE email LIKE 'deleted\_%@system.local';

CREATE TABLE IF NOT EXISTS user_followers (
    follower_id UUID NOT NULL,
    following_id UUID NOT NULL,
    PRIMARY KEY (follower_id, following_id),
    CONSTRAINT fk_user_followers_follower FOREIGN KEY (follower_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_followers_following FOREIGN KEY (following_id) REFERENCES users (id) ON DELETE CASCADE
);
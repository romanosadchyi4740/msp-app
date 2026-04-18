create table chats
(
    id         uuid         not null
        primary key,
    created_at timestamp(6),
    updated_at timestamp(6)
);

create table chat_participants
(
    chat_id uuid not null
        constraint chat_id_fk
            references chats,
    user_id uuid not null
        constraint user_id_fk
            references users,
    primary key (chat_id, user_id)
);

create table messages
(
    id              uuid         not null
        primary key,
    content         text         not null,
    timestamp       timestamp(6) not null,
    chat_id         uuid         not null
        constraint chat_id_fk
            references chats,
    sender_id       uuid         not null
        constraint sender_id_fk
            references users,
    shared_track_id uuid
        constraint shared_track_id_fk
            references tracks
);

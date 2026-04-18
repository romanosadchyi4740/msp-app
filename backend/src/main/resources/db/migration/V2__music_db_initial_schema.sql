create table authors
(
    id           uuid         not null
        primary key,
    created_at   timestamp(6),
    updated_at   timestamp(6),
    avatar_url   text,
    biography    text,
    display_name varchar(255) not null
        constraint display_name_unique
            unique
);

create table albums
(
    id           uuid         not null
        primary key,
    created_at   timestamp(6),
    updated_at   timestamp(6),
    cover_url    text,
    description  text,
    release_date date,
    status       varchar(255)
        constraint albums_status_check
            check ((status)::text = ANY
        ((ARRAY ['DRAFT'::character varying, 'PUBLISHED'::character varying, 'ARCHIVED'::character varying])::text[])),
    title        varchar(255) not null,
    author_id    uuid         not null
        constraint author_id_fk
            references authors
);

create table genres
(
    id         uuid         not null
        primary key,
    created_at timestamp(6),
    updated_at timestamp(6),
    name       varchar(255) not null
        constraint name_unique
            unique,
    slug       varchar(255) not null
        constraint slug_unique
            unique
);

create table playlists
(
    id          uuid         not null
        primary key,
    created_at  timestamp(6),
    updated_at  timestamp(6),
    cover_url   text,
    description text,
    is_public   boolean,
    owner_id    uuid         not null,
    title       varchar(255) not null
);

create table tracks
(
    id           uuid         not null
        primary key,
    created_at   timestamp(6),
    updated_at   timestamp(6),
    cover_url    text,
    duration     integer      not null,
    file_key     varchar(255) not null,
    title        varchar(255) not null,
    track_number integer,
    album_id     uuid
        constraint album_id_fk
            references albums,
    author_id    uuid         not null
        constraint author_id_fk
            references authors
);

create table playlist_tracks
(
    playlist_id uuid    not null
        constraint playlist_id_fk
            references playlists,
    track_id    uuid    not null
        constraint track_id_fk
            references tracks,
    track_order integer not null,
    primary key (playlist_id, track_order)
);

create table track_genres
(
    track_id uuid not null
        constraint track_id_fk
            references tracks,
    genre_id uuid not null
        constraint genre_id_fk
            references genres,
    primary key (track_id, genre_id)
);

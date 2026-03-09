create table public.users
(
    id            uuid         not null
        primary key,
    created_at    timestamp(6),
    updated_at    timestamp(6),
    avatar_url    text,
    display_name  varchar(255),
    email         varchar(255) not null
        constraint email_unique
            unique,
    password      varchar(255),
    auth_provider varchar(255)
        constraint users_auth_provider_check
            check ((auth_provider)::text = ANY
                   ((ARRAY ['GOOGLE'::character varying, 'LOCAL'::character varying])::text[])),
    provider_id   varchar(255),
    role          varchar(255) not null
        constraint users_role_check
            check ((role)::text = ANY
                   ((ARRAY ['ROLE_USER'::character varying, 'ROLE_ARTIST'::character varying, 'ROLE_ADMIN'::character varying])::text[]))
);

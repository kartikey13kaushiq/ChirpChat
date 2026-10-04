create table users (
    id            uuid primary key,
    username      varchar(32)  not null,
    display_name  varchar(80)  not null,
    password_hash varchar(100) not null,
    created_at    timestamptz  not null
);
create unique index users_username_uq on users (lower(username));

-- Opaque refresh tokens, stored hashed. Tokens issued from one login share a family; presenting
-- an already-rotated token revokes the whole family (refresh-token reuse detection).
create table refresh_tokens (
    id          uuid primary key,
    user_id     uuid        not null references users (id) on delete cascade,
    family_id   uuid        not null,
    token_hash  varchar(64) not null unique,
    expires_at  timestamptz not null,
    created_at  timestamptz not null,
    rotated_at  timestamptz,
    revoked_at  timestamptz
);
create index refresh_tokens_family_idx on refresh_tokens (family_id);

-- Users live in the auth service. This service stores the ids and usernames it needs,
-- taken from verified tokens or from the auth service's directory.
create table channels (
    id              uuid primary key,
    name            varchar(40)  not null,
    topic           varchar(250),
    is_private      boolean      not null,
    created_by      uuid         not null,
    last_seq        bigint       not null default 0,
    last_message_at timestamptz,
    created_at      timestamptz  not null
);
create unique index channels_name_uq on channels (lower(name));

create table channel_members (
    id            uuid primary key,
    channel_id    uuid        not null references channels (id) on delete cascade,
    user_id       uuid        not null,
    username      varchar(32) not null,
    role          varchar(10) not null check (role in ('OWNER', 'MEMBER')),
    last_read_seq bigint      not null default 0,
    joined_at     timestamptz not null,
    unique (channel_id, user_id)
);
create index channel_members_user_idx on channel_members (user_id);

create table messages (
    id              uuid primary key,
    channel_id      uuid          not null references channels (id) on delete cascade,
    author_id       uuid          not null,
    author_username varchar(32)   not null,
    seq             bigint        not null,
    body            varchar(4000) not null,
    mentions        varchar(32)[] not null default '{}',
    created_at      timestamptz   not null,
    unique (channel_id, seq)
);

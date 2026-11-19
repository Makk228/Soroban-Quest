create table identity.refresh_tokens (
    id         uuid        primary key,
    user_id    uuid        not null references identity.users (id) on delete cascade,
    token_hash char(64)    not null unique,
    issued_at  timestamptz not null,
    expires_at timestamptz not null,
    revoked_at timestamptz
);

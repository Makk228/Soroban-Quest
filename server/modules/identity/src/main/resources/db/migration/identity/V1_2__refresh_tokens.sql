create table identity.refresh_tokens (
    id         uuid        primary key,
    user_id    uuid        not null references identity.users (id) on delete cascade,
    token_hash char(64)    not null unique, -- SHA-256 в hex; сам токен не хранится
    issued_at  timestamptz not null,
    expires_at timestamptz not null,
    revoked_at timestamptz
);

create index refresh_tokens_user_idx on identity.refresh_tokens (user_id) where revoked_at is null;
create index refresh_tokens_expires_idx on identity.refresh_tokens (expires_at);

create table leaderboard.entries (
    period_type varchar(16) not null check (period_type in ('WEEKLY', 'ALL_TIME')),
    period_key  varchar(16) not null,
    user_id     uuid        not null,
    xp          bigint      not null check (xp >= 0),
    sessions    integer     not null check (sessions >= 0),
    updated_at  timestamptz not null,
    primary key (period_type, period_key, user_id)
);

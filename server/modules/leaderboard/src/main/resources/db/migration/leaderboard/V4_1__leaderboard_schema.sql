create schema if not exists leaderboard;

create table leaderboard.entries (
    period_type varchar(16) not null check (period_type in ('WEEKLY', 'ALL_TIME')),
    period_key  varchar(16) not null, -- '2026-W43' либо 'ALL'
    user_id     uuid        not null,
    xp          bigint      not null default 0 check (xp >= 0),
    sessions    integer     not null default 0 check (sessions >= 0),
    updated_at  timestamptz not null default now(),
    primary key (period_type, period_key, user_id)
);

-- выборка топа периода: индекс покрывает сортировку по убыванию опыта
create index entries_period_xp_idx on leaderboard.entries (period_type, period_key, xp desc);

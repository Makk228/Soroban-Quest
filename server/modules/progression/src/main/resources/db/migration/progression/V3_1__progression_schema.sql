create schema if not exists progression;

create table progression.learner_profiles (
    user_id             uuid        primary key,
    total_xp            bigint      not null default 0 check (total_xp >= 0),
    current_streak_days integer     not null default 0 check (current_streak_days >= 0),
    longest_streak_days integer     not null default 0 check (longest_streak_days >= 0),
    last_training_date  date,
    total_sessions      integer     not null default 0,
    total_tasks         integer     not null default 0,
    total_correct       integer     not null default 0,
    daily_goal_xp       integer     not null default 100 check (daily_goal_xp between 20 and 5000),
    created_at          timestamptz not null,
    updated_at          timestamptz not null,
    version             bigint      not null default 0
);

-- таблица лидеров по общему опыту строится по этому индексу
create index learner_profiles_total_xp_idx on progression.learner_profiles (total_xp desc);

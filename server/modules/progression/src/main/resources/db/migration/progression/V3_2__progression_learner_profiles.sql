create table progression.learner_profiles (
    user_id             uuid        primary key,
    total_xp            bigint      not null check (total_xp >= 0),
    current_streak_days integer     not null check (current_streak_days >= 0),
    longest_streak_days integer     not null check (longest_streak_days >= 0),
    last_training_date  date,
    total_sessions      integer     not null,
    total_tasks         integer     not null,
    total_correct       integer     not null,
    daily_goal_xp       integer     not null check (daily_goal_xp between 20 and 5000),
    created_at          timestamptz not null,
    updated_at          timestamptz not null,
    version             bigint      not null
);

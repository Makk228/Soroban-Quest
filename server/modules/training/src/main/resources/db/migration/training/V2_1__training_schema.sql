-- Модуль training владеет собственной схемой: границы модулей видны и в БД.
create schema if not exists training;

create table training.practice_sessions (
    id                uuid        primary key,
    user_id           uuid        not null,
    drill_kind        varchar(32) not null,
    difficulty        smallint    not null check (difficulty between 1 and 10),
    seed              bigint      not null,
    task_count        smallint    not null check (task_count > 0),
    generator_version smallint    not null,
    status            varchar(16) not null check (status in ('IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    answered_count    smallint    not null default 0 check (answered_count >= 0),
    correct_count     smallint    not null default 0 check (correct_count >= 0),
    current_combo     smallint    not null default 0,
    best_combo        smallint    not null default 0,
    score             integer     not null default 0,
    total_time_ms     bigint      not null default 0,
    started_at        timestamptz not null,
    completed_at      timestamptz,
    version           bigint      not null default 0,
    constraint practice_sessions_answered_within_tasks check (answered_count <= task_count),
    constraint practice_sessions_correct_within_answered check (correct_count <= answered_count)
);

-- история тренировок пользователя
create index practice_sessions_user_started_idx
    on training.practice_sessions (user_id, started_at desc);

-- быстрый подсчёт активных сессий (лимит незавершённых тренировок)
create index practice_sessions_active_idx
    on training.practice_sessions (user_id)
    where status = 'IN_PROGRESS';

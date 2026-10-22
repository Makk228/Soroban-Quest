create table training.session_answers (
    session_id      uuid        not null references training.practice_sessions (id) on delete cascade,
    task_index      smallint    not null check (task_index >= 0),
    given_answer    bigint      not null,
    expected_answer bigint      not null,
    correct         boolean     not null,
    elapsed_ms      integer     not null check (elapsed_ms > 0),
    awarded_points  integer     not null check (awarded_points >= 0),
    answered_at     timestamptz not null,
    -- составной первичный ключ делает повторную отправку ответа безопасной:
    -- параллельные запросы не могут дважды засчитать одно и то же задание
    primary key (session_id, task_index)
);

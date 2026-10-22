create table progression.skill_mastery (
    user_id                uuid        not null,
    drill_kind             varchar(32) not null,
    sessions               integer     not null default 0,
    tasks_answered         integer     not null default 0,
    correct_answers        integer     not null default 0,
    best_score             integer     not null default 0,
    best_average_answer_ms bigint,
    updated_at             timestamptz not null,
    primary key (user_id, drill_kind)
);

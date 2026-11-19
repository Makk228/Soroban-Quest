create table progression.skill_mastery (
    user_id                uuid        not null,
    drill_kind             varchar(32) not null,
    sessions               integer     not null,
    tasks_answered         integer     not null,
    correct_answers        integer     not null,
    best_score             integer     not null,
    best_average_answer_ms bigint,
    updated_at             timestamptz not null,
    primary key (user_id, drill_kind)
);

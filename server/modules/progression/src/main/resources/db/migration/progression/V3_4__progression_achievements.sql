create table progression.achievements (
    user_id     uuid        not null,
    code        varchar(32) not null,
    unlocked_at timestamptz not null,

    primary key (user_id, code)
);

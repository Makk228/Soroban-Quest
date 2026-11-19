create table progression.daily_progress (
    user_id       uuid    not null,
    progress_date date    not null,
    xp            integer not null check (xp >= 0),
    sessions      integer not null check (sessions >= 0),
    primary key (user_id, progress_date)
);

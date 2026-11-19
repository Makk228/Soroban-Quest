create table identity.users (
    id            uuid         primary key,
    email         varchar(254) not null,
    display_name  varchar(32)  not null,
    password_hash varchar(72)  not null,
    created_at    timestamptz  not null,
    active        boolean      not null
);

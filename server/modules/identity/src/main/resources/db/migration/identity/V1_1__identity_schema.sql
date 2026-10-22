-- Схема модуля identity. Межмодульных внешних ключей нет: модули общаются
-- через API и события, а не через связи в БД.
create schema if not exists identity;

create table identity.users (
    id            uuid         primary key,
    email         varchar(254) not null,
    display_name  varchar(32)  not null,
    password_hash varchar(72)  not null,
    created_at    timestamptz  not null,
    active        boolean      not null default true
);

-- уникальность адреса — на уровне БД, чтобы гонка регистраций не создала дубль
create unique index users_email_uidx on identity.users (lower(email));

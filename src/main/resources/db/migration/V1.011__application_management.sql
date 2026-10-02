-- What the frontend's "My applications" pages need on top of the application table #84 created.
--
-- Applications are addressed in URLs by public_id, not by the bigserial key: a sequential id in a
-- URL invites guessing, and the frontend (like amp-auth before it) treats ids as opaque strings.
-- The default also covers rows that already exist; gen_random_uuid() is built in from Postgres 13.
--
-- custom_attributes and connected_apis are JSON held as text: they are only ever read and written
-- whole by the service, never queried into, so there is nothing for jsonb to buy.
alter table application
    add column description        text,
    add column public_id          uuid not null default gen_random_uuid(),
    add column public_key_url     text,
    add column callback_url       text,
    add column custom_attributes  text not null default '{}',
    add column connected_apis     text not null default '[]';

create unique index application_public_id_idx on application (public_id);

-- The same name can be registered once per environment per owner, never twice in one.
create unique index application_owner_name_environment_idx
    on application (user_id, lower(name), environment);

-- Client secrets. Only a bcrypt hash is kept: the secret is shown once, when it is made, and never
-- again; key_preview (its last four characters) is all the UI can show afterwards. Revoked rows
-- are kept so the list can show that a secret existed and when it stopped working.
-- Deliberately not application_api_key, which holds APIM subscription keys - a different thing.
create table application_secret (
    id              bigserial   primary key,
    public_id       uuid        not null default gen_random_uuid() unique,
    application_id  bigint      not null references application(id) on delete cascade,
    key_hash        text        not null,
    key_preview     text        not null,
    created_at      timestamp   not null,
    revoked_at      timestamp
);

create index application_secret_application_idx on application_secret (application_id);

-- People other than the owner who can work on an application. Matched on email rather than a
-- user id, so someone can be invited before they have registered and gain access when they do.
-- The owner is not a row here: they always outrank both roles.
create table application_team_member (
    id              bigserial   primary key,
    public_id       uuid        not null default gen_random_uuid() unique,
    application_id  bigint      not null references application(id) on delete cascade,
    email           text        not null,
    role            text        not null check (role in ('developer', 'administrator')),
    added_at        timestamp   not null
);

create unique index application_team_member_email_idx
    on application_team_member (application_id, lower(email));

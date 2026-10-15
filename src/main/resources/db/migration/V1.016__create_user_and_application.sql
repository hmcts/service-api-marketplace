-- A person is whoever Entra says they are. entra_object_id is the oid claim and the only stable
-- identity: email can change and a display name is not unique. No password column - Entra owns
-- sign-in - and no stored reference, because "user001" is formatted from id.
create table marketplace_user (
    id                bigserial    primary key,
    entra_object_id   text         not null unique,
    email             text         not null unique,
    first_name        text         not null,
    last_name         text         not null,
    status            text         not null default 'ACTIVE',
    created_at        timestamp    not null,
    last_login_at     timestamp    not null
);

-- appname is the cleaned name both the Entra and APIM names are built from: amp-user001-<appname>
-- and amp-user001-<appname>-<api>. Unique per user rather than globally, because the subscription
-- name already carries the user reference and two people may each have a "case-viewer".
--
-- The client secret itself is never stored: Entra returns it once and has no call that reads it
-- back. secret_hint is the first three characters, so a screen can show "Uc6****". secret_key_id
-- is the passwordCredential id, which regenerating needs to remove the one it replaces.
create table application (
    id                bigserial    primary key,
    user_id           bigint       not null references marketplace_user(id),
    name              text         not null,
    appname           text         not null,
    client_id         text         not null unique,
    object_id         text         not null,
    secret_hint       text         not null,
    secret_key_id     text         not null,
    secret_expires_at timestamp    not null,
    created_at        timestamp    not null
);

-- One row per API, rather than a field on application, because this is queryable: when a publisher
-- withdraws an API the question is which applications hold it. subscription_name is stored rather
-- than rebuilt from the parts - APIM cannot rename a subscription, so a naming change would
-- otherwise orphan every existing one. key_hint is three characters, never the key.
create table application_api (
    id                bigserial    primary key,
    application_id    bigint       not null references application(id) on delete cascade,
    short_code        text         not null,
    product_id        text         not null,
    subscription_name text         not null,
    key_hint          text         not null,
    subscribed_at     timestamp    not null
);

create unique index application_user_appname_idx on application (user_id, appname);
create unique index application_api_unique_idx on application_api (application_id, short_code);
create index application_user_idx on application (user_id);
create index application_api_short_code_idx on application_api (short_code);

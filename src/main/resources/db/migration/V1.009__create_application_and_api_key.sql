-- A marketplace_user registers an application to get a Client ID/Secret from Entra, then
-- subscribes it to one or more APIs to get a Subscription Key per API - the same two-step
-- credential model already proven in the prototype (see
-- API_Marketplace_Prototype_Technical_Reference.md). Deliberately not named
-- subscription_request/publish_request: those are review-workflow rows with a status and a
-- human-readable reference; these are provisioned credentials, issued immediately, with
-- nothing to review.
create table application (
    id            bigserial   primary key,
    user_id       integer     not null references marketplace_user(id),
    name          text        not null,
    environment   text        not null,
    client_id     text        not null,
    created_at    timestamp   not null
);

-- One row per (application, api) pair - an application can subscribe to several APIs without
-- ever needing a new Client ID/Secret, mirroring api_subscriptions in the prototype schema.
create table application_api_key (
    id                bigserial   primary key,
    application_id    bigint      not null references application(id) on delete cascade,
    api_short_code    text        not null,
    publisher_id      text        not null,
    subscription_key  text        not null,
    created_at        timestamp   not null
);

create index application_user_idx on application(user_id);
create index application_api_key_application_idx on application_api_key(application_id);

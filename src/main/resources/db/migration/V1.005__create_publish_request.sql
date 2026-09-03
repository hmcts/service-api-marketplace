-- A request to publish an API in the marketplace. Mirrors subscription_request: the same
-- requester and lifecycle columns, then the fields its own form asks for.
--
-- The requester is stamped from the user the request is submitted by, not sent by the
-- client, so org_name, user_name and user_email are derived rather than supplied.
create table publish_request (
    id            uuid      primary key not null,
    org_name      text      not null,
    user_name     text      not null,
    user_email    text      not null,
    status        text      not null default 'NEW',
    submitted_at  timestamp not null,
    api_name      text,
    owning_team   text,
    contact_email text,
    spec_url      text
);

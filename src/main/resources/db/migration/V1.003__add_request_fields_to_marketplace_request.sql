drop table if exists marketplace_request;

create table marketplace_request (
    id              uuid        primary key not null,
    type            text        not null,
    org_name        text        not null,
    user_name       text        not null,
    user_email      text        not null,
    status          text        not null default 'NEW',
    submitted_at    timestamp   not null,
    api_short_code  text,
    api             text,
    environment     text,
    expected_volume text,
    use_case        text,
    oauth2_capable  boolean     not null default false,
    declaration     text
);

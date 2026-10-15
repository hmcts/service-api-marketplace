drop table if exists marketplace_request;

create table marketplace_request (
    id              uuid        primary key not null,
    type            text        not null,
    org_name        text        not null,
    user_name       text        not null,
    user_email      text        not null,
    status          text        not null,
    submitted_at    timestamp   not null
);

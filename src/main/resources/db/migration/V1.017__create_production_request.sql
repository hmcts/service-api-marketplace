-- A request for production credentials for an application that already exists in sandbox.
create table production_request (
    id                    bigserial    primary key,
    user_id               bigint       not null references marketplace_user (id),
    application_id        bigint       references application (id) on delete set null,
    application_name      text         not null,
    reference             text         not null unique,
    status                text         not null default 'SUBMITTED',
    submitted_at          timestamp    not null,
    organisation          text         not null,
    go_live_date          date         not null,
    call_volume           text         not null,
    dpia                  text         not null,
    dsa                   text         not null,
    security_contact      text         not null,
    use_case              text         not null,
    declaration_confirmed boolean      not null
);

create index production_request_user_idx on production_request (user_id);

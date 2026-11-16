-- No foreign key to marketplace_user, so someone can be granted before they first sign in.
create table marketplace_admin (
    id                bigserial    primary key,
    entra_object_id   uuid         not null unique,
    granted_at        timestamp    not null default (now() at time zone 'utc')
);

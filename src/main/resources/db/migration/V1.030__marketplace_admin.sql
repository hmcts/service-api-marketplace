-- Who may see every user, application and production request rather than only their own. The
-- service checks this on every request; the web app only uses it to decide whether to show the
-- Admin pages.
--
-- Keyed on the Entra object id (the oid claim) rather than email, because this repository is
-- public. It is matched to marketplace_user by a join, not a foreign key, so a person can be
-- granted before they have ever signed in. The price is that a grant can silently match nobody:
-- an Entra account deleted and recreated comes back with a new oid, and UserService keeps the
-- existing marketplace_user row on email but moves it to the new oid.
--
-- Stored lower case, as UserService stores every oid, so the join is a plain equality.
--
-- Granting is a migration per person: no endpoint writes to this table. Seed rows go in their own
-- migrations, one per change, so the history says who was granted and when.
create table marketplace_admin (
    id                bigserial    primary key,
    entra_object_id   text         not null unique,
    granted_at        timestamp    not null default (now() at time zone 'utc'),
    constraint marketplace_admin_oid_format
        check (entra_object_id ~ '^[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}$')
);

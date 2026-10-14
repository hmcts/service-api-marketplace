-- Approved organisations are identified by the email domains of their people (the "organisation approval"
-- design). A person signing in with Entra whose domain is listed here is active; one whose domain is not is
-- held until an admin approves them.
create table organisation_domain (
    id          serial      primary key,
    org_id      integer     not null references organisation(id),
    domain      text        not null,
    created_at  timestamp   not null default now()
);

-- One organisation per domain, compared without regard to case.
create unique index organisation_domain_domain_idx on organisation_domain (lower(domain));

-- A person who signs in with Entra has no local password, and a held person has no organisation yet.
alter table marketplace_user alter column password_hash drop not null;
alter table marketplace_user alter column org_id drop not null;

-- requested_role: a producer who has asked and not yet been approved. approved_by / approved_at: the admin who
-- last approved, suspended or reinstated them. status is ACTIVE, HELD (waiting for approval) or SUSPENDED.
alter table marketplace_user
    add column requested_role text,
    add column approved_by    text,
    add column approved_at    timestamp;

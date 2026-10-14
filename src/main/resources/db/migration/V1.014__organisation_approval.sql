-- Approved organisations are identified by the email domains of their people (the "organisation approval"
-- design). A person signing in with Entra whose email domain is listed here may use the marketplace; anyone else
-- is refused. There is no queue and no approval of individuals: the list of domains is the approval.
create table organisation_domain (
    id          serial      primary key,
    org_id      integer     not null references organisation(id),
    domain      text        not null,
    created_at  timestamp   not null default now()
);

-- One organisation per domain, compared without regard to case.
create unique index organisation_domain_domain_idx on organisation_domain (lower(domain));

-- A person who signs in with Entra has no local password.
alter table marketplace_user alter column password_hash drop not null;

-- approved_by / approved_at: the admin who last suspended or reinstated this person, and when. status is ACTIVE or
-- SUSPENDED.
alter table marketplace_user
    add column approved_by    text,
    add column approved_at    timestamp;

-- Entra External ID sign-in (AMP-1284). The web app records each person Entra signs in, keyed by Entra's
-- object id (oid), so the same person is recognised on every sign-in whatever their email becomes.

alter table marketplace_user
    add column if not exists entra_object_id text;

-- One Entra user belongs to at most one account; accounts without one do not compete.
create unique index if not exists marketplace_user_entra_object_id_idx
    on marketplace_user (entra_object_id)
    where entra_object_id is not null;

-- People who sign in with Entra have no password here. A null hash never matches, so password sign-in
-- fails closed for them.
alter table marketplace_user
    alter column password_hash drop not null;

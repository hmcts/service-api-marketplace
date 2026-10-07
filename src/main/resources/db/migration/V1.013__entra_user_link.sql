-- When an account is also created in Microsoft Entra (ACCOUNT_IDENTITY=entra), keep Entra's object id for it:
-- it is what deleting that Entra user, or recognising the same person when they sign in with Entra, needs.
-- Null for every account that exists today, and for any made without Entra.
alter table marketplace_user
    add column entra_object_id text;

-- One Entra user belongs to at most one account; accounts without one do not compete.
create unique index marketplace_user_entra_object_id_idx
    on marketplace_user (entra_object_id)
    where entra_object_id is not null;

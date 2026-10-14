-- When each account was created, and when its owner last signed in with Entra. Accounts that
-- predate this take the time of the migration as their creation time, the nearest there is;
-- last_signed_in_at stays empty until their next Entra sign-in.

alter table marketplace_user
    add column created_at timestamp not null default (now() at time zone 'utc');

alter table marketplace_user
    add column last_signed_in_at timestamp;

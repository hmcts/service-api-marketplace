-- The reference is the only identifier the API accepts or hands out, so the surrogate
-- uuid no longer earns its keep. Dropping the column takes its primary key with it,
-- which leaves the reference free to become the key it already behaves like.
--
-- The unique index from V1.006 is redundant once the reference is the primary key -
-- Postgres backs a primary key with its own unique index - so it goes too. The
-- not-null and format constraints from V1.006 and V1.007 still apply.

alter table subscription_request drop column id;

alter table subscription_request add primary key (reference);

drop index if exists subscription_request_reference_idx;

alter table publish_request drop column id;

alter table publish_request add primary key (reference);

drop index if exists publish_request_reference_idx;

-- A uuid key bought nothing once the reference became the only identifier the API accepts
-- or returns: it is now purely an internal row identifier, and a bigserial is cheaper to
-- store and index and reads better in a log. Existing rows are renumbered from the new
-- sequence, which is safe because nothing outside the database ever held the old value.
--
-- The reference keeps the unique index V1.006 gave it and the format check from V1.007, so
-- it stays as strongly guaranteed as it was - it simply is not the key.

alter table subscription_request drop column id;

alter table subscription_request add column id bigserial primary key;

alter table publish_request drop column id;

alter table publish_request add column id bigserial primary key;

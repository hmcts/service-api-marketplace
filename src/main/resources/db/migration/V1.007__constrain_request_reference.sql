-- The reference is the only identifier the API now exposes for a request, so the
-- column is narrowed to exactly the shape ReferenceGenerator produces: a two-letter
-- RequestType prefix, a four-digit year and a six-character [A-Z0-9] suffix.
-- A UUID renders as 36 characters and so can no longer be written here by mistake.
--
-- The prefix is left as any two letters rather than (AR|PR) so that adding a
-- RequestType stays a change to the enum alone. The suffix length mirrors
-- ReferenceGenerator.SUFFIX_LENGTH; changing that constant needs a migration to
-- match, which is deliberate - a shorter suffix means more collisions.

alter table subscription_request alter column reference type varchar(14);

alter table subscription_request
    add constraint subscription_request_reference_format
    check (reference ~ '^[A-Z]{2}-[0-9]{4}-[A-Z0-9]{6}$');

alter table publish_request alter column reference type varchar(14);

alter table publish_request
    add constraint publish_request_reference_format
    check (reference ~ '^[A-Z]{2}-[0-9]{4}-[A-Z0-9]{6}$');

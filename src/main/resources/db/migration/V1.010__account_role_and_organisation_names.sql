-- Self-service registration needs three things the seeded accounts never did.
--
-- 1. V1.001 inserted the seed organisation with an explicit id (1) into a serial column, which
--    leaves the sequence at 1. The first organisation created through the application would
--    therefore try to take id 1 and fail with a duplicate key. Move the sequence past the
--    highest id that exists (and, when the table is somehow empty, leave it at the start).
select setval(
    pg_get_serial_sequence('organisation', 'id'),
    coalesce((select max(id) from organisation), 1),
    (select count(*) > 0 from organisation));

-- 2. A user is either a consumer of APIs or a producer of them. Everyone seeded so far consumes.
alter table marketplace_user
    add column role text not null default 'consumer'
        check (role in ('consumer', 'producer'));

-- 3. Registration takes the organisation as free text and finds or creates it, so two people typing
--    "HMCTS" and "hmcts" must land on one row rather than two.
create unique index organisation_name_lower_idx on organisation (lower(name));

-- Each API has its own Entra resource app, so the role no longer names the API: the audience is
-- the boundary and the role says what you may do within it. The predicate makes this idempotent,
-- which is what lets it land on a database where an earlier version of it already ran.
update marketplace_api set role = 'app.read' where role <> 'app.read';

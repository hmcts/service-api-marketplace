-- Each API has its own Entra resource app, so the role no longer names the API: the audience is
-- the boundary and the role says what you may do within it. The predicate is what makes this
-- idempotent rather than a rewrite of every row on each run.
update marketplace_api set role = 'app.read' where role <> 'app.read';

-- Each API has its own Entra resource app, so the role no longer names the API: the audience is
-- the boundary and the role says what you may do within it.
update marketplace_api set role = 'app.read';

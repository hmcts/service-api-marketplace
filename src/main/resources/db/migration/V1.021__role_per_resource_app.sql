-- Each API has its own Entra resource app, so the role no longer needs to name the API. The
-- audience is the boundary; the role says what you may do within it.
update marketplace_api set role = 'read';

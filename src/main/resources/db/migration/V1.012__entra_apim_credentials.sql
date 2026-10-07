-- What the service needs to remember once an application's credentials are real (APPLICATION_CREDENTIALS=entra),
-- rather than made up by the service itself.
--
-- A real credential can be taken away again only by naming it to the system that issued it, so the names
-- have to be kept. All three columns are nullable or defaulted: every row that exists today was made without
-- Entra or APIM, and stays that way.

-- Whether the application's Client ID belongs to a real Entra application, so that deleting the application
-- knows to delete that too. Not worked out from the Client ID: a made-up one is just the public id, which
-- is a guess nobody should have to rely on.
alter table application
    add column entra_registered boolean not null default false;

-- Entra's id for a client secret, which is what revoking it needs. The secret itself is still never kept,
-- only its hash and last four characters.
alter table application_secret
    add column entra_key_id text;

-- APIM's name for the subscription behind a subscription key, which is what deleting it needs.
alter table application_api_key
    add column subscription_name text;

-- Connecting and disconnecting an API look the rows up by application and API.
create index application_api_key_application_api_idx
    on application_api_key (application_id, api_short_code);

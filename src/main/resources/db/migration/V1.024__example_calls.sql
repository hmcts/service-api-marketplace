-- The requests a consumer is shown for each API, after the token request: one or more per API, in
-- order, each a path under the API's gateway_path. Held here so a new API needs only its rows
create table marketplace_api_call (
    id         bigserial primary key,
    short_code text    not null references marketplace_api (short_code),
    position   integer not null,
    title      text    not null,
    path       text    not null,
    unique (short_code, position)
);

insert into marketplace_api_call (short_code, position, title, path)
  values
    ('slc', 1, 'Get the court schedule for a case', '/case/{case_urn}/courtschedule'),
    ('rcc', 1, 'Get a court house',                 '/courthouses/{court_id}'),
    ('rcc', 2, 'Get a courtroom in a court house',  '/courthouses/{court_id}/courtrooms/{court_room_id}');

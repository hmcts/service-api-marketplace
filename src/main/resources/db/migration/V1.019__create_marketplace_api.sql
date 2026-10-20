-- The marketplace's API catalogue. One row per API, keyed by the short code, because that is the
-- only identifier that is short, stable and already everywhere
create table marketplace_api (
    short_code            text  primary key,
    title                 text  not null,
    catalogue_name        text  not null unique,
    product_id            text  not null,
    try_it_now_product_id text,
    gateway_path          text  not null,
    role                  text  not null
);

insert into marketplace_api (short_code, title, catalogue_name, product_id, try_it_now_product_id, gateway_path, role)
  values
    ('slc', 'Scheduling and Listing Court Schedule', 'api-cp-crime-schedulingandlisting-courtschedule', 'cp-crime-schedulingandlisting', 'try-it-now-cp-crime-schedulingandlisting', 'amp/slc', 'slc.read');
insert into marketplace_api (short_code, title, catalogue_name, product_id, try_it_now_product_id, gateway_path, role)
  values
    ('rcc', 'Refdata Court Hearing Courthouses',     'api-cp-refdata-courthearing-courthouses',         'cp-refdata-courthearing',       'try-it-now-cp-refdata-courthearing', 'amp/rcc', 'rcc.read');

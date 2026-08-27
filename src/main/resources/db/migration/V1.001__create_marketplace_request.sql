drop table if exists onboarding_request;

create table organisation (
    id          serial      primary key,
    name        text        not null
);

create table marketplace_user (
    id              serial      primary key,
    org_id          integer     not null references organisation(id),
    first_name      text        not null,
    last_name       text        not null,
    email           text        not null unique,
    password_hash   text        not null,
    status          text        not null default 'ACTIVE'
);

create table marketplace_request (
    id              uuid        primary key not null,
    type            text        not null,
    payload         text        not null,
    status          text        not null,
    submitted_at    timestamp   not null
);

insert into organisation (id, name) values (1, 'Api Marketplace');

insert into marketplace_user (first_name, last_name, email, org_id, password_hash, status) values
    ('Colin',        'Greenwood',  'colin.greenwood@hmcts.net',           1, 'CHANGE_ME', 'ACTIVE'),
    ('Nagashankar',  'Ponnaganti', 'nagashankar.ponnaganti@hmcts.net',    1, 'CHANGE_ME', 'ACTIVE'),
    ('Zaheer',       'Iqbal',      'zaheer.iqbal@hmcts.net',              1, 'CHANGE_ME', 'ACTIVE'),
    ('Amandip',      'Singh',      'amandip.singh3@hmcts.net',            1, 'CHANGE_ME', 'ACTIVE'),
    ('Duncan',       'Crawford',   'duncan.crawford@hmcts.net',           1, 'CHANGE_ME', 'ACTIVE');

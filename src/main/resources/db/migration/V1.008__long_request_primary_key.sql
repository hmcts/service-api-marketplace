alter table subscription_request drop column id;

alter table subscription_request add column id bigserial primary key;

alter table publish_request drop column id;

alter table publish_request add column id bigserial primary key;

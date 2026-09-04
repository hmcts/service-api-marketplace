delete from subscription_request;
delete from publish_request;

alter table subscription_request add column reference text not null;
alter table publish_request add column reference text not null;

create unique index subscription_request_reference_idx on subscription_request (reference);
create unique index publish_request_reference_idx on publish_request (reference);

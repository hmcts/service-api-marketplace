alter table production_request alter column dpia drop not null;
alter table production_request alter column declaration_confirmed drop not null;

alter table production_request add column data_request text;
alter table production_request add column proposed_outputs text;

alter table production_request alter column dsa drop not null;
alter table production_request alter column security_contact drop not null;
alter table production_request alter column use_case drop not null;

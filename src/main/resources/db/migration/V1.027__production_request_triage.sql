-- The questions the Data Access Team triage a request on. data_recipients is a set rather than
-- one value: a request can send data to several places at once, and which ones decides whether
-- an explanation is needed.
alter table production_request add column organisation_type           text;
alter table production_request add column data_recipients             text[];
alter table production_request add column data_recipients_explanation text;
alter table production_request add column data_combined               text;
alter table production_request add column data_combined_description   text;

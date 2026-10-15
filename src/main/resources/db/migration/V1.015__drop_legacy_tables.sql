-- Everything the removed endpoints owned. The replacement marketplace_user and application are a
-- separate migration in a separate change, so this leaves the schema deliberately empty: nothing
-- in the application reads or writes a table after this point.
--
-- Dropped child first, because application_api_key references application and application
-- references marketplace_user. "if exists" because some environments ran migrations from branches
-- that never reached master, so their tables may or may not be here.
drop table if exists application_api_key;
drop table if exists application_secret;
drop table if exists application_team_member;
drop table if exists application;
drop table if exists subscription_request;
drop table if exists publish_request;
drop table if exists marketplace_user;
drop table if exists organisation;

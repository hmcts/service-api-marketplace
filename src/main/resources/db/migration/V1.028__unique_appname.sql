-- Entra and APIM names are built from appname alone, so it must be unique across all users.
drop index application_user_appname_idx;
create unique index application_appname_idx on application (appname);

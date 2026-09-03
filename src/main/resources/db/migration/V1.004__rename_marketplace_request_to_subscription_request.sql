-- The table holds subscription requests specifically, and a second kind of request —
-- publication — is about to need its own. Renamed rather than dropped and recreated as the
-- earlier migrations did: there are live rows in the deployed environments by now, and a
-- rename keeps them.
alter table marketplace_request rename to subscription_request;

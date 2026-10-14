-- Accounts no longer say whether their owner consumes or publishes APIs: the marketplace stopped
-- asking. Drops the column V1.010 added, with its check.
alter table marketplace_user
    drop column role;

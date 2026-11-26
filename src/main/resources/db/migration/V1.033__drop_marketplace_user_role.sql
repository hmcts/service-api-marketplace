-- Admin is the amp_admin app role in Entra, verified from the token on /admin paths.
alter table marketplace_user
    drop constraint if exists marketplace_user_role_check,
    drop column if exists role;

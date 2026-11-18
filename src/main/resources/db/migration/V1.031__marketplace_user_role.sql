alter table marketplace_user
    add column role text not null default 'USER',
    add constraint marketplace_user_role_check check (role in ('USER', 'ADMIN'));

alter table instructor add column user_id uuid unique references app_user(id);

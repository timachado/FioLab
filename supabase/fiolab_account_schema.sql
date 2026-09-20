-- FioLab account schema.
-- Applied to Supabase project dwpcddiramxlhavdmmyn on 2026-09-20.
-- Kept here so the backend shape is versioned with the Android app.

create schema if not exists private;

create table if not exists public.fiolab_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default '' check (char_length(display_name) <= 80),
  avatar_url text null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.fiolab_subscriptions (
  user_id uuid primary key references auth.users(id) on delete cascade,
  plan_code text not null default 'free' check (char_length(plan_code) between 1 and 40),
  status text not null default 'active'
    check (status in ('active','trialing','past_due','canceled','expired')),
  source text not null default 'fiolab',
  current_period_end timestamptz null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.fiolab_profiles enable row level security;
alter table public.fiolab_subscriptions enable row level security;

create policy "fiolab_profiles_select_own"
on public.fiolab_profiles for select to authenticated
using ((select auth.uid()) = user_id);

create policy "fiolab_profiles_insert_own"
on public.fiolab_profiles for insert to authenticated
with check ((select auth.uid()) = user_id);

create policy "fiolab_profiles_update_own"
on public.fiolab_profiles for update to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

create policy "fiolab_subscriptions_select_own"
on public.fiolab_subscriptions for select to authenticated
using ((select auth.uid()) = user_id);

grant select, insert, update on public.fiolab_profiles to authenticated;
grant select on public.fiolab_subscriptions to authenticated;
revoke all on public.fiolab_profiles from anon;
revoke all on public.fiolab_subscriptions from anon;

create or replace function private.fiolab_create_default_subscription()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.fiolab_subscriptions(user_id)
  values (new.user_id)
  on conflict (user_id) do nothing;
  return new;
end;
$$;

revoke all on function private.fiolab_create_default_subscription()
from public, anon, authenticated;

create trigger fiolab_profile_default_subscription
after insert on public.fiolab_profiles
for each row
execute function private.fiolab_create_default_subscription();

create or replace function private.fiolab_handle_auth_user_created()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  app_slug text;
  display_name_value text;
begin
  app_slug := coalesce(new.raw_user_meta_data ->> 'app_slug', '');
  if app_slug <> 'fiolab' then
    return new;
  end if;

  display_name_value := left(
    coalesce(
      nullif(new.raw_user_meta_data ->> 'display_name', ''),
      split_part(coalesce(new.email, ''), '@', 1),
      ''
    ),
    80
  );

  insert into public.fiolab_profiles(user_id, display_name)
  values (new.id, display_name_value)
  on conflict (user_id) do nothing;

  return new;
end;
$$;

revoke all on function private.fiolab_handle_auth_user_created()
from public, anon, authenticated;

create trigger fiolab_auth_user_created
after insert on auth.users
for each row
execute function private.fiolab_handle_auth_user_created();

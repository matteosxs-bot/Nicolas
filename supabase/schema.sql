-- Entre Noses cloud schema
-- Run this file in the Supabase SQL Editor before enabling cloud accounts.
create extension if not exists pgcrypto;

create table if not exists public.households (
  id uuid primary key default gen_random_uuid(),
  name text not null check (length(trim(name)) between 1 and 80),
  invite_code text not null unique default upper(substr(encode(gen_random_bytes(8), 'hex'), 1, 10)),
  created_by uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);

create table if not exists public.household_members (
  household_id uuid not null references public.households(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null default 'member' check (role in ('owner','member')),
  joined_at timestamptz not null default now(),
  primary key (household_id, user_id)
);

create table if not exists public.private_snapshots (
  user_id uuid primary key references auth.users(id) on delete cascade,
  payload jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

create table if not exists public.shared_records (
  household_id uuid not null references public.households(id) on delete cascade,
  record_id uuid not null,
  record_type text not null check (record_type in ('transaction','goal','shopping','budget','bill','vehicle','pet','subscription','debt','account','credit_card','stock')),
  payload jsonb not null default '{}'::jsonb,
  updated_by uuid not null references auth.users(id),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  primary key (household_id, record_id)
);

create table if not exists public.shared_snapshots (
  household_id uuid primary key references public.households(id) on delete cascade,
  payload jsonb not null default '{}'::jsonb,
  updated_by uuid not null references auth.users(id),
  updated_at timestamptz not null default now()
);

alter table public.households enable row level security;
alter table public.household_members enable row level security;
alter table public.private_snapshots enable row level security;
alter table public.shared_snapshots enable row level security;
alter table public.shared_records enable row level security;

create or replace function public.is_household_member(target_household uuid)
returns boolean
language sql stable security definer
set search_path = public
as $$
  select auth.uid() is not null and exists (
    select 1 from public.household_members
    where household_id = target_household and user_id = auth.uid()
  );
$;

-- Membership checks are only callable by signed-in users. Policies use this
-- function under the authenticated role; anonymous callers get no signal.
revoke all on function public.is_household_member(uuid) from public;
revoke all on function public.is_household_member(uuid) from anon;
grant execute on function public.is_household_member(uuid) to authenticated;

create policy "members can read their households"
on public.households for select to authenticated
using (public.is_household_member(id));

create policy "owners can update their household"
on public.households for update to authenticated
using (created_by = auth.uid())
with check (created_by = auth.uid());

create policy "members can read membership"
on public.household_members for select to authenticated
using (user_id = auth.uid() or public.is_household_member(household_id));

create policy "users manage only their private snapshot"
on public.private_snapshots for all to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

create policy "household members can read shared snapshot"
on public.shared_snapshots for select to authenticated
using (public.is_household_member(household_id));

create policy "household members can create shared snapshot"
on public.shared_snapshots for insert to authenticated
with check (public.is_household_member(household_id) and updated_by = auth.uid());

create policy "household members can update shared snapshot"
on public.shared_snapshots for update to authenticated
using (public.is_household_member(household_id))
with check (public.is_household_member(household_id) and updated_by = auth.uid());

create policy "members can read shared records"
on public.shared_records for select to authenticated
using (public.is_household_member(household_id));

create policy "members can insert shared records"
on public.shared_records for insert to authenticated
with check (public.is_household_member(household_id) and updated_by = auth.uid());

create policy "members can update shared records"
on public.shared_records for update to authenticated
using (public.is_household_member(household_id))
with check (public.is_household_member(household_id) and updated_by = auth.uid());

create policy "members can delete shared records"
on public.shared_records for delete to authenticated
using (public.is_household_member(household_id));

create or replace function public.create_household(household_name text)
returns table(household_id uuid, invite_code text)
language plpgsql security definer
set search_path = public
as $$
declare new_id uuid;
declare new_code text;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  if length(trim(household_name)) not between 1 and 80 then raise exception 'Invalid household name'; end if;
  insert into public.households(name, created_by)
    values (trim(household_name), auth.uid())
    returning id, households.invite_code into new_id, new_code;
  insert into public.household_members(household_id, user_id, role)
    values (new_id, auth.uid(), 'owner');
  return query select new_id, new_code;
end;
$$;

create or replace function public.join_household(code text)
returns uuid
language plpgsql security definer
set search_path = public
as $$
declare target_id uuid;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  select id into target_id from public.households
    where invite_code = upper(trim(code)) limit 1;
  if target_id is null then raise exception 'Invalid invitation code'; end if;
  -- Serialize joins for this household so two simultaneous requests cannot
  -- both observe one free slot and create a third member.
  perform pg_advisory_xact_lock(hashtextextended(target_id::text, 0));
  if exists (select 1 from public.household_members where household_id = target_id and user_id = auth.uid()) then
    return target_id;
  end if;
  if (select count(*) from public.household_members where household_id = target_id) >= 2 then
    raise exception 'This household already has two members';
  end if;
  insert into public.household_members(household_id, user_id, role)
    values (target_id, auth.uid(), 'member');
  return target_id;
end;
$$;

revoke all on function public.create_household(text) from public;
revoke all on function public.join_household(text) from public;
grant execute on function public.create_household(text) to authenticated;
grant execute on function public.join_household(text) to authenticated;

grant select, update on public.households to authenticated;
grant select on public.household_members to authenticated;
grant select, insert, update, delete on public.private_snapshots to authenticated;
grant select, insert, update on public.shared_snapshots to authenticated;
grant select, insert, update, delete on public.shared_records to authenticated;

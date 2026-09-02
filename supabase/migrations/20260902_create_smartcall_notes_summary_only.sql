-- Smart Calls stores only the generated summary.
-- Audio files and full transcripts stay on the device / selected local export.
create table if not exists public.smartcall_notes (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid()
    references auth.users(id) on delete cascade,
  phone text not null,
  contact_id text,
  contact_name text,
  call_started_at timestamptz not null default now(),
  duration_seconds integer not null
    check (duration_seconds > 60),
  summary text not null
    check (btrim(summary) <> ''),
  source_file_name text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index if not exists smartcall_notes_user_source_key
  on public.smartcall_notes (user_id, source_file_name);

create index if not exists smartcall_notes_user_started_idx
  on public.smartcall_notes (user_id, call_started_at desc);

create index if not exists smartcall_notes_user_phone_idx
  on public.smartcall_notes (user_id, phone);

alter table public.smartcall_notes enable row level security;

drop policy if exists smartcall_notes_select_own
  on public.smartcall_notes;
create policy smartcall_notes_select_own
  on public.smartcall_notes
  for select
  to authenticated
  using ((select auth.uid()) = user_id);

drop policy if exists smartcall_notes_insert_own
  on public.smartcall_notes;
create policy smartcall_notes_insert_own
  on public.smartcall_notes
  for insert
  to authenticated
  with check ((select auth.uid()) = user_id);

drop policy if exists smartcall_notes_update_own
  on public.smartcall_notes;
create policy smartcall_notes_update_own
  on public.smartcall_notes
  for update
  to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

drop policy if exists smartcall_notes_delete_own
  on public.smartcall_notes;
create policy smartcall_notes_delete_own
  on public.smartcall_notes
  for delete
  to authenticated
  using ((select auth.uid()) = user_id);

grant select, insert, update, delete on public.smartcall_notes to authenticated;
grant select, insert, update, delete on public.smartcall_notes to service_role;
revoke all on public.smartcall_notes from anon;

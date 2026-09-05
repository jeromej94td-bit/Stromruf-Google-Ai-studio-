create table if not exists public.customer_notes (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
    contact_id text,
    contact_name text,
    phone text not null,
    note text not null check (length(btrim(note)) > 0),
    source text not null default 'activity'
        check (source in ('activity', 'call_mask', 'smart_call', 'other')),
    occurred_at_ms bigint not null
        default ((extract(epoch from now()) * 1000)::bigint),
    created_at timestamptz not null default now()
);

comment on table public.customer_notes is
    'Freie Kundennotizen; Gesprächsversuche bleiben in call_logs und werden in der App zu einer Zeitleiste verbunden.';

alter table public.customer_notes enable row level security;

drop policy if exists "customer_notes_select_own" on public.customer_notes;
create policy "customer_notes_select_own"
on public.customer_notes
for select
to authenticated
using ((select auth.uid()) = user_id);

drop policy if exists "customer_notes_insert_own" on public.customer_notes;
create policy "customer_notes_insert_own"
on public.customer_notes
for insert
to authenticated
with check ((select auth.uid()) = user_id);

drop policy if exists "customer_notes_update_own" on public.customer_notes;
create policy "customer_notes_update_own"
on public.customer_notes
for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

drop policy if exists "customer_notes_delete_own" on public.customer_notes;
create policy "customer_notes_delete_own"
on public.customer_notes
for delete
to authenticated
using ((select auth.uid()) = user_id);

revoke all on table public.customer_notes from anon;
grant select, insert, update, delete on table public.customer_notes to authenticated;
grant select, insert, update, delete on table public.customer_notes to service_role;

create index if not exists customer_notes_user_contact_time_idx
    on public.customer_notes (user_id, contact_id, occurred_at_ms desc);

create index if not exists customer_notes_user_phone_time_idx
    on public.customer_notes (user_id, phone, occurred_at_ms desc);

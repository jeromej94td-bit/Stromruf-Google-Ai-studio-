alter table public.contacts
    add column if not exists customer_number text;

alter table public.neukunden
    add column if not exists customer_name text,
    add column if not exists company text,
    add column if not exists email text,
    add column if not exists delivery_address text,
    add column if not exists meter_number text,
    add column if not exists consumption bigint,
    add column if not exists energy_type text,
    add column if not exists next_action_at bigint,
    add column if not exists offer_sent_at bigint,
    add column if not exists completed_at bigint,
    add column if not exists archived_at bigint,
    add column if not exists updated_at_ms bigint not null default 0;

update public.neukunden
set updated_at_ms = date_created
where updated_at_ms = 0;

create index if not exists neukunden_user_status_idx
    on public.neukunden (user_id, status);

create index if not exists neukunden_user_next_action_idx
    on public.neukunden (user_id, next_action_at)
    where completed_at is null and archived_at is null;

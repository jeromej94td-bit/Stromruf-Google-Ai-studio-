-- ------------------------------------------------------------
-- 1) Protokoll aller Agenten-Aktionen (Audit + Vorschlagsliste)
-- ------------------------------------------------------------
create table if not exists public.agent_actions (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null default auth.uid(),
  session_id   uuid references public.agent_call_sessions(id) on delete cascade,
  agent_name   text,
  tool_name    text not null,
  arguments    jsonb not null default '{}'::jsonb,
  reason       text,                    -- Begründung des Agenten
  status       text not null default 'ausgefuehrt'
                 check (status in ('ausgefuehrt','vorgeschlagen','verworfen','fehler')),
  result       jsonb,
  error        text,
  applied_at   timestamptz,
  created_at   timestamptz not null default now()
);

create index if not exists idx_aa_session on public.agent_actions (session_id);
create index if not exists idx_aa_user    on public.agent_actions (user_id, created_at desc);

-- ------------------------------------------------------------
-- 2) Werkzeug-Berechtigungen pro Nutzer
-- ------------------------------------------------------------
create table if not exists public.agent_tool_policy (
  user_id       uuid primary key default auth.uid(),
  auto_apply    boolean not null default false,   -- false = erst vorschlagen
  allowed_tools text[] not null default array[
    'kontakt_suchen',
    'kontakt_anlegen',
    'kontakt_aktualisieren',
    'wiedervorlage_anlegen',
    'gespraechsergebnis_setzen',
    'notiz_an_anruf',
    'hotbox_setzen',
    'kontakt_sperren'
  ],
  max_actions   integer not null default 8 check (max_actions between 1 and 20),
  extra_prompt  text default '',                  -- eigene Zusatzregeln
  updated_at    timestamptz not null default now()
);

-- ------------------------------------------------------------
-- 3) RLS
-- ------------------------------------------------------------
alter table public.agent_actions     enable row level security;
alter table public.agent_tool_policy enable row level security;

do $$
declare t text;
begin
  foreach t in array array['agent_actions','agent_tool_policy']
  loop
    execute format('drop policy if exists "%1$s_all_own" on public.%1$s', t);
    execute format(
      'create policy "%1$s_all_own" on public.%1$s
         for all using (auth.uid() = user_id) with check (auth.uid() = user_id)', t);
  end loop;
end $$;

-- Horrorcraft-LightsOut schema. Re-runnable. Creates everything it needs.
-- LESSONS: state both halves per table (GRANT + RLS). Base tables closed to anon;
-- the watcher reads narrow views only. Insert plainly; no ON CONFLICT on partial indexes.

-- gen_random_uuid() is core since Postgres 13; no extension needed.

-- ---------- runs ----------
create table if not exists runs (
  id          uuid primary key default gen_random_uuid(),
  name        text not null,
  world_seed  text not null,
  rng_seed    bigint not null,                -- seeded rolls, replayable
  control     boolean not null default false, -- true = no-killer baseline run
  started_at  timestamptz not null default now(),
  ended_at    timestamptz,
  ending      text check (ending in ('rescue','too_late','total_loss'))
);
create unique index if not exists runs_one_open on runs ((true)) where ended_at is null;

-- ---------- actors per run ----------
create table if not exists campers (
  run_id    uuid not null references runs(id) on delete cascade,
  name      text not null,                     -- josh, dane, mara, tyler, alex, reyes, hale, killer
  role      text not null check (role in ('camper','human','police','killer')),
  trait     text,                              -- persona used this run (swappable)
  lives     int  not null default 3 check (lives between 0 and 3),
  alive     boolean not null default true,
  died_at   timestamptz,
  primary key (run_id, name)
);

-- ---------- speech (agents, Alex, police) ----------
create table if not exists utterances (
  id         bigint generated always as identity primary key,
  run_id     uuid not null references runs(id) on delete cascade,
  at         timestamptz not null default now(),
  speaker    text not null,
  addressee  text,
  text       text not null,
  x double precision, y double precision, z double precision,
  heard_by   text[] not null default '{}',     -- proximity recipients
  audio_path text,                             -- archived ElevenLabs line
  voiced     boolean not null default true     -- false for '*' lines
);

-- ---------- stimuli: everything the director does ----------
create table if not exists stimuli (
  id      bigint generated always as identity primary key,
  run_id  uuid not null references runs(id) on delete cascade,
  at      timestamptz not null default now(),
  kind    text not null,   -- sign, sound, reveal, vanish, lights_out, body_moved, chase, roll, radio, dispatch, storm...
  target  text,
  detail  jsonb not null default '{}'::jsonb,
  x double precision, y double precision, z double precision
);

-- ---------- rolls ----------
create table if not exists rolls (
  id            bigint generated always as identity primary key,
  run_id        uuid not null references runs(id) on delete cascade,
  at            timestamptz not null default now(),
  target        text not null,
  hits_attempted int not null check (hits_attempted between 1 and 3),
  hits_landed    int check (hits_landed between 0 and 3),
  blocked_by     text                           -- guard that vetoed it, if any
);

-- ---------- survival choices under threat ----------
create table if not exists survival_actions (
  id         bigint generated always as identity primary key,
  run_id     uuid not null references runs(id) on delete cascade,
  at         timestamptz not null default now(),
  camper     text not null,
  action     text not null check (action in ('run','hide','silent','shout','fight','light','none')),
  dodge_pct  int,
  dodged     boolean,
  seen_death boolean not null default false     -- had this camper seen a death yet
);

-- ---------- positions (cohesion, exploration, map) ----------
create table if not exists positions (
  run_id uuid not null references runs(id) on delete cascade,
  at     timestamptz not null default now(),
  name   text not null,
  x double precision not null, y double precision not null, z double precision not null
);
create index if not exists positions_run_at on positions (run_id, at);

-- ---------- bodies and discoveries ----------
create table if not exists bodies (
  run_id  uuid not null references runs(id) on delete cascade,
  name    text not null,
  x double precision, y double precision, z double precision,
  moved   boolean not null default false,
  primary key (run_id, name)
);

create table if not exists discoveries (
  id       bigint generated always as identity primary key,
  run_id   uuid not null references runs(id) on delete cascade,
  at       timestamptz not null default now(),
  finder   text not null,
  body     text not null
);

-- ---------- memory lines (belief drift) ----------
create table if not exists memory_lines (
  id      bigint generated always as identity primary key,
  run_id  uuid not null references runs(id) on delete cascade,
  at      timestamptz not null default now(),
  camper  text not null,
  kind    text not null check (kind in ('fact','belief')),
  text    text not null,
  refusal boolean not null default false        -- out-of-character / refusal detected
);

-- ---------- privileges: base tables closed ----------
do $$ declare t text; begin
  foreach t in array array['runs','campers','utterances','stimuli','rolls','survival_actions',
                           'positions','bodies','discoveries','memory_lines'] loop
    execute format('alter table %I enable row level security', t);
    execute format('revoke all on %I from anon, authenticated', t);
  end loop;
end $$;

-- ---------- watcher views (read-only, narrow columns) ----------
create or replace view watch_feed as
  select run_id, at, speaker, addressee, text, heard_by from utterances;
create or replace view watch_campers as
  select run_id, name, role, lives, alive, died_at from campers;
create or replace view watch_events as
  select run_id, at, kind, target, x, z from stimuli;
create or replace view watch_rolls as
  select run_id, at, target, hits_attempted, hits_landed from rolls;
create or replace view watch_positions as
  select run_id, at, name, x, z from positions;

grant select on watch_feed, watch_campers, watch_events, watch_rolls, watch_positions to anon;
-- Bridge writes with the service key only. Verify with the publishable key that
-- watch_* returns rows and base tables return permission denied.

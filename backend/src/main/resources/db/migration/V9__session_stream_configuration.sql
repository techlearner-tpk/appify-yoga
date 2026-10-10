-- A session override takes precedence over its program default, including disabled overrides.
create table session_stream_configuration (
  id uuid primary key,
  program_id uuid unique references program(id),
  session_id uuid unique references session(id),
  provider_type text not null check(provider_type in ('ZOOM','YOUTUBE')),
  enabled boolean not null default true,
  encrypted_configuration text not null,
  updated_at timestamptz not null default now(),
  check(num_nonnulls(program_id,session_id)=1)
);
alter table attendance add column provider_type text not null default 'YOUTUBE';
alter table playback_session add column provider_type text not null default 'YOUTUBE';

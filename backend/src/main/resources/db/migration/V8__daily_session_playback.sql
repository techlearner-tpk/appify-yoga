alter table class_series add column sequence_number integer not null default 1;
with ordered as (
  select id,row_number() over(partition by program_id order by local_time,id) as position from class_series
)
update class_series c set sequence_number=o.position from ordered o where o.id=c.id;

create table program_session_policy (
  program_id uuid primary key references program(id),
  timezone text not null default 'Asia/Kolkata',
  duration_minutes integer not null default 45 check(duration_minutes between 1 and 360),
  join_early_minutes integer not null default 5 check(join_early_minutes between 0 and 60),
  close_late_minutes integer not null default 5 check(close_late_minutes between 0 and 60),
  daily_attendance_limit integer not null default 1 check(daily_attendance_limit=1),
  source_series_id uuid references class_series(id),
  cleanup_delay_minutes integer not null default 15 check(cleanup_delay_minutes between 0 and 1440),
  cleanup_enabled boolean not null default false,
  max_delete_attempts integer not null default 5 check(max_delete_attempts between 1 and 20),
  updated_at timestamptz not null default now()
);
insert into program_session_policy(program_id,timezone,duration_minutes,source_series_id)
select p.id,coalesce(first_series.timezone,'Asia/Kolkata'),coalesce(first_series.duration_minutes,p.duration_minutes),first_series.id
from program p left join lateral (
  select id,timezone,duration_minutes from class_series where program_id=p.id order by sequence_number,id limit 1
) first_series on true;

create table daily_session_asset (
  id uuid primary key,
  program_id uuid not null references program(id),
  local_date date not null,
  timezone text not null,
  provider_type text not null default 'YOUTUBE',
  provider_asset_id text,
  provider_owned boolean not null default false,
  provider_broadcast_id text,
  provider_stream_id text,
  source_session_slot_id uuid,
  asset_status text not null default 'CREATING',
  recording_status text not null default 'NOT_READY',
  access_expires_at timestamptz not null,
  deletion_status text not null default 'NONE',
  deletion_requested_at timestamptz,
  next_delete_attempt_at timestamptz,
  deleted_at timestamptz,
  delete_attempts integer not null default 0,
  last_delete_error text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(program_id,local_date)
);
create index idx_daily_asset_expiry on daily_session_asset(access_expires_at) where asset_status<>'ACCESS_EXPIRED';
create index idx_daily_asset_cleanup on daily_session_asset(next_delete_attempt_at) where deletion_status='PENDING';

alter table session add column local_date date;
alter table session add column daily_asset_id uuid references daily_session_asset(id);
alter table session add column sequence_number integer not null default 1;
update session set local_date=(starts_at at time zone timezone)::date;
insert into daily_session_asset(id,program_id,local_date,timezone,provider_asset_id,asset_status,recording_status,access_expires_at)
select gen_random_uuid(),program_id,local_date,timezone,max(nullif(youtube_video_id,'')),
       case when max(nullif(youtube_video_id,'')) is null then 'CREATING' else 'AVAILABLE' end,
       case when max(nullif(youtube_video_id,'')) is null then 'NOT_READY' else 'AVAILABLE' end,
       ((local_date+1)::timestamp at time zone timezone)
from session group by program_id,local_date,timezone
on conflict(program_id,local_date) do nothing;
update session s set daily_asset_id=a.id
from daily_session_asset a
where a.program_id=s.program_id and a.local_date=s.local_date;
update session s set sequence_number=c.sequence_number from class_series c where s.series_id=c.id;
update daily_session_asset a set source_session_slot_id=(
  select s.id from session s where s.daily_asset_id=a.id order by s.sequence_number,s.starts_at,s.id limit 1
);
alter table daily_session_asset add constraint fk_daily_asset_source_slot foreign key(source_session_slot_id) references session(id);
alter table session alter column local_date set not null;
alter table session alter column daily_asset_id set not null;
alter table session drop column youtube_video_id;
alter table class_series drop column youtube_video_id;

alter table program_enrollment add column active boolean not null default true;
create table membership_entitlement (
  user_id uuid primary key references app_user(id),
  status text not null default 'TRIAL',
  valid_until timestamptz not null,
  updated_at timestamptz not null default now()
);
insert into membership_entitlement(user_id,status,valid_until)
select id,'TRIAL',now()+interval '30 days' from app_user on conflict do nothing;

create table user_daily_participation (
  id uuid primary key,
  user_id uuid not null references app_user(id),
  program_id uuid not null references program(id),
  local_date date not null,
  timezone text not null,
  selected_session_slot_id uuid references session(id),
  joined_session_slot_id uuid references session(id),
  status text not null default 'NOT_JOINED',
  first_joined_at timestamptz,
  qualified_at timestamptz,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(user_id,program_id,local_date)
);
insert into user_daily_participation(id,user_id,program_id,local_date,timezone,selected_session_slot_id,joined_session_slot_id,status,first_joined_at,qualified_at,completed_at)
select gen_random_uuid(),a.user_id,s.program_id,s.local_date,s.timezone,s.id,s.id,
       case when bool_or(a.qualified) then 'QUALIFIED' when bool_or(a.left_at is not null) then 'COMPLETED' else 'JOINED' end,
       min(a.joined_at),min(a.updated_at) filter(where a.qualified),max(a.left_at)
from attendance a join session s on s.id=a.session_id
group by a.user_id,s.program_id,s.local_date,s.timezone,s.id
on conflict(user_id,program_id,local_date) do nothing;

create table playback_session (
  id uuid primary key,
  user_id uuid not null references app_user(id),
  session_slot_id uuid not null references session(id),
  daily_session_asset_id uuid not null references daily_session_asset(id),
  token_hash text not null unique,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  last_heartbeat_at timestamptz not null default now(),
  status text not null default 'ACTIVE',
  concurrent_warning boolean not null default false
);
create index idx_playback_session_user on playback_session(user_id,status,expires_at);

create table session_join_link (
  id uuid primary key,
  token_hash text not null unique,
  user_id uuid not null references app_user(id),
  session_slot_id uuid not null references session(id),
  program_id uuid not null references program(id),
  local_date date not null,
  expires_at timestamptz not null,
  revoked boolean not null default false,
  created_at timestamptz not null default now()
);
create index idx_join_link_user on session_join_link(user_id,session_slot_id,expires_at);

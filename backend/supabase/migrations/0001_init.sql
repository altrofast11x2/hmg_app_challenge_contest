-- DriveLog M1 스키마
-- 조직, 구성원(관리자·운전자, 열람자는 M2), 차량, 운전자, 연결 코드, 운행, 수정 이력, 해시 체인 머리
-- 권한은 RLS로 강제한다. service_role 키는 서버 작업(seed)에서만 쓰고 앱·웹에는 절대 넣지 않는다.

create extension if not exists pgcrypto with schema extensions;

create type public.app_role as enum ('admin', 'driver', 'viewer');

-- ─── 테이블 ──────────────────────────────────────────────────────────────

create table public.organizations (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  created_at timestamptz not null default now()
);

-- 사람 계정(관리자·운전자·열람자)과 조직의 관계. 차량은 사람 계정이 아니라 vehicles.device_user_id로 연결된다
create table public.memberships (
  user_id uuid not null references auth.users (id) on delete cascade,
  org_id uuid not null references public.organizations (id) on delete cascade,
  role public.app_role not null,
  display_name text,
  created_at timestamptz not null default now(),
  primary key (user_id, org_id)
);

-- 운전자. 로그인 계정이 없어도 된다(user_id가 있으면 그 사람이 자기 운행을 볼 수 있다)
create table public.drivers (
  id uuid primary key default gen_random_uuid(),
  org_id uuid not null references public.organizations (id) on delete cascade,
  name text not null,
  user_id uuid references auth.users (id) on delete set null,
  created_at timestamptz not null default now()
);

-- 차량. id는 앱의 차량 ID(VIN, 없으면 앱이 만든 기기 ID). device_user_id는 차량 앱의 익명 로그인 계정
create table public.vehicles (
  id text primary key,
  org_id uuid not null references public.organizations (id) on delete cascade,
  vin text,
  display_name text,
  default_driver_id uuid references public.drivers (id) on delete set null,
  device_user_id uuid unique references auth.users (id) on delete set null,
  paired_at timestamptz,
  created_at timestamptz not null default now()
);

-- 관리자 웹에서 발급하는 6자리 연결 코드 (10분 유효, 한 번만 사용). reusable은 시연용 데모 코드
create table public.pairing_codes (
  code text primary key check (code ~ '^[0-9]{6}$'),
  org_id uuid not null references public.organizations (id) on delete cascade,
  vehicle_name text,
  created_by uuid references auth.users (id) on delete set null,
  expires_at timestamptz not null,
  reusable boolean not null default false,
  used_at timestamptz,
  used_by_vehicle text,
  created_at timestamptz not null default now()
);

-- 연결 코드 대입 시도를 막기 위한 기록 (함수 안에서만 쓴다)
create table public.pairing_attempts (
  id bigint generated always as identity primary key,
  user_id uuid not null,
  succeeded boolean not null,
  attempted_at timestamptz not null default now()
);
create index pairing_attempts_user_time on public.pairing_attempts (user_id, attempted_at);

-- 완료된 운행. id는 앱이 만든 UUID라 같은 운행을 여러 번 올려도 한 줄만 남는다
-- 시각은 해시 체인 검증을 위해 앱과 같은 epoch 밀리초로 저장하고, 조회용 timestamptz는 계산 열로 둔다
create table public.trips (
  id uuid primary key,
  org_id uuid not null references public.organizations (id) on delete cascade,
  vehicle_id text not null references public.vehicles (id) on delete cascade,
  driver_id uuid references public.drivers (id) on delete set null,
  started_at_ms bigint not null,
  ended_at_ms bigint,
  started_at timestamptz generated always as (to_timestamp(started_at_ms / 1000.0)) stored,
  ended_at timestamptz generated always as (to_timestamp(ended_at_ms / 1000.0)) stored,
  start_odometer_km numeric(10, 1),
  end_odometer_km numeric(10, 1),
  odometer_source text not null default 'VEHICLE',
  start_soc_percent int,
  end_soc_percent int,
  efficiency numeric,
  efficiency_type text,
  start_address text,
  end_address text,
  visit_note text,
  purpose text not null check (purpose in ('UNCLASSIFIED', 'COMMUTE', 'BUSINESS', 'PERSONAL')),
  chain_seq bigint not null,
  prev_hash text not null,
  hash text not null,
  synced_at timestamptz not null default now(),
  unique (vehicle_id, chain_seq)
);
create index trips_org_started on public.trips (org_id, started_at_ms);

-- 분류·수정 이력. 지우지 않고 쌓는다 (비업무로 바뀌면 위치 값만 해시값으로 가린다)
create table public.trip_changes (
  id uuid primary key,
  org_id uuid not null references public.organizations (id) on delete cascade,
  trip_id uuid not null references public.trips (id) on delete cascade,
  vehicle_id text not null references public.vehicles (id) on delete cascade,
  field text not null,
  old_value text,
  new_value text,
  actor text not null,
  reason text,
  created_at_ms bigint not null,
  chain_seq bigint not null,
  prev_hash text not null,
  hash text not null,
  synced_at timestamptz not null default now(),
  unique (vehicle_id, chain_seq)
);

-- 차량별 해시 체인의 마지막 위치 (끝부분 삭제를 찾아내는 기준점)
create table public.chain_heads (
  vehicle_id text primary key references public.vehicles (id) on delete cascade,
  org_id uuid not null references public.organizations (id) on delete cascade,
  seq bigint not null,
  hash text not null,
  updated_at timestamptz not null default now()
);

-- ─── 권한 판단 함수 (RLS에서 자기 테이블을 다시 읽다가 무한 반복하지 않도록 security definer) ───

create function public.has_org_role(p_org uuid, p_roles public.app_role[])
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.memberships m
    where m.org_id = p_org and m.user_id = auth.uid() and m.role = any (p_roles)
  );
$$;

create function public.is_vehicle_device(p_vehicle text)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.vehicles v where v.id = p_vehicle and v.device_user_id = auth.uid());
$$;

create function public.device_org()
returns uuid language sql stable security definer set search_path = public as $$
  select v.org_id from public.vehicles v where v.device_user_id = auth.uid();
$$;

create function public.is_driver_user(p_driver uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.drivers d where d.id = p_driver and d.user_id = auth.uid());
$$;

-- ─── RLS ──────────────────────────────────────────────────────────────

alter table public.organizations enable row level security;
alter table public.memberships enable row level security;
alter table public.drivers enable row level security;
alter table public.vehicles enable row level security;
alter table public.pairing_codes enable row level security;
alter table public.pairing_attempts enable row level security;
alter table public.trips enable row level security;
alter table public.trip_changes enable row level security;
alter table public.chain_heads enable row level security;

-- 조직: 구성원과 그 조직의 차량이 볼 수 있다
create policy organizations_select on public.organizations for select
  using (public.has_org_role(id, '{admin,driver,viewer}') or id = public.device_org());

-- 구성원: 자기 행, 관리자는 조직 전체
create policy memberships_select on public.memberships for select
  using (user_id = auth.uid() or public.has_org_role(org_id, '{admin}'));

-- 운전자: 구성원·차량이 보고, 관리자가 추가·수정·삭제한다
create policy drivers_select on public.drivers for select
  using (public.has_org_role(org_id, '{admin,driver,viewer}') or org_id = public.device_org());
create policy drivers_insert on public.drivers for insert
  with check (public.has_org_role(org_id, '{admin}'));
create policy drivers_update on public.drivers for update
  using (public.has_org_role(org_id, '{admin}')) with check (public.has_org_role(org_id, '{admin}'));
create policy drivers_delete on public.drivers for delete
  using (public.has_org_role(org_id, '{admin}'));

-- 차량: 등록은 pair_vehicle 함수로만. 관리자가 이름·기본 운전자를 고치고 삭제(연결 해제)한다
create policy vehicles_select on public.vehicles for select
  using (public.has_org_role(org_id, '{admin,driver,viewer}') or device_user_id = auth.uid());
create policy vehicles_update on public.vehicles for update
  using (public.has_org_role(org_id, '{admin}')) with check (public.has_org_role(org_id, '{admin}'));
create policy vehicles_delete on public.vehicles for delete
  using (public.has_org_role(org_id, '{admin}'));

-- 연결 코드: 발급은 issue_pairing_code 함수로만. 관리자가 보고 지운다
create policy pairing_codes_select on public.pairing_codes for select
  using (public.has_org_role(org_id, '{admin}'));
create policy pairing_codes_delete on public.pairing_codes for delete
  using (public.has_org_role(org_id, '{admin}'));

-- pairing_attempts: 정책 없음 (함수만 접근)

-- 운행: 관리자·열람자는 조직 전체, 운전자는 자기 운행, 차량은 자기 운행을 본다. 쓰기는 차량만 (관리자는 고칠 수 없다)
create policy trips_select on public.trips for select
  using (
    public.has_org_role(org_id, '{admin,viewer}')
    or (public.has_org_role(org_id, '{driver}') and public.is_driver_user(driver_id))
    or public.is_vehicle_device(vehicle_id)
  );
create policy trips_insert on public.trips for insert
  with check (public.is_vehicle_device(vehicle_id) and org_id = public.device_org());
create policy trips_update on public.trips for update
  using (public.is_vehicle_device(vehicle_id))
  with check (public.is_vehicle_device(vehicle_id) and org_id = public.device_org());

create policy trip_changes_select on public.trip_changes for select
  using (
    public.has_org_role(org_id, '{admin,viewer}')
    or exists (select 1 from public.trips t where t.id = trip_id and public.is_driver_user(t.driver_id))
    or public.is_vehicle_device(vehicle_id)
  );
create policy trip_changes_insert on public.trip_changes for insert
  with check (public.is_vehicle_device(vehicle_id) and org_id = public.device_org());
create policy trip_changes_update on public.trip_changes for update
  using (public.is_vehicle_device(vehicle_id))
  with check (public.is_vehicle_device(vehicle_id) and org_id = public.device_org());

create policy chain_heads_select on public.chain_heads for select
  using (public.has_org_role(org_id, '{admin,viewer}') or public.is_vehicle_device(vehicle_id));
create policy chain_heads_insert on public.chain_heads for insert
  with check (public.is_vehicle_device(vehicle_id) and org_id = public.device_org());
create policy chain_heads_update on public.chain_heads for update
  using (public.is_vehicle_device(vehicle_id))
  with check (public.is_vehicle_device(vehicle_id) and org_id = public.device_org());

-- ─── 함수 (RPC) ──────────────────────────────────────────────────────────

-- 관리자가 6자리 연결 코드를 발급한다 (10분 유효)
create function public.issue_pairing_code(p_org uuid, p_vehicle_name text default null)
returns table (code text, expires_at timestamptz)
language plpgsql security definer set search_path = public as $$
declare
  v_code text;
  v_bytes bytea;
  v_expires timestamptz := now() + interval '10 minutes';
begin
  if not public.has_org_role(p_org, '{admin}') then
    raise exception 'FORBIDDEN' using errcode = '42501';
  end if;
  loop
    v_bytes := extensions.gen_random_bytes(4);
    v_code := lpad(((get_byte(v_bytes, 0)::bigint * 16777216 + get_byte(v_bytes, 1) * 65536
                     + get_byte(v_bytes, 2) * 256 + get_byte(v_bytes, 3)) % 1000000)::text, 6, '0');
    exit when not exists (
      select 1 from public.pairing_codes c
      where c.code = v_code and c.expires_at > now() and (c.reusable or c.used_at is null)
    );
  end loop;
  -- 같은 번호의 만료·사용된 코드가 남아 있으면 지우고 새로 발급한다
  delete from public.pairing_codes c where c.code = v_code;
  insert into public.pairing_codes (code, org_id, vehicle_name, created_by, expires_at)
  values (v_code, p_org, p_vehicle_name, auth.uid(), v_expires);
  return query select v_code, v_expires;
end;
$$;

-- 차량 앱(익명 로그인 계정)이 연결 코드로 조직에 등록한다.
-- 실패 기록이 남도록 예외 대신 status를 돌려준다: OK, INVALID_CODE, EXPIRED_CODE, TOO_MANY_ATTEMPTS,
-- VEHICLE_IN_OTHER_ORG(다른 조직에서 쓰는 차량. 그 조직 관리자가 차량을 삭제해야 옮길 수 있다), UNAUTHENTICATED
create function public.pair_vehicle(p_code text, p_vehicle_id text, p_vin text default null)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := auth.uid();
  v_code public.pairing_codes;
  v_existing public.vehicles;
  v_driver uuid;
  v_driver_name text;
  v_org_name text;
  v_failures int;
begin
  if v_uid is null then
    return jsonb_build_object('status', 'UNAUTHENTICATED');
  end if;

  select count(*) into v_failures from public.pairing_attempts
  where user_id = v_uid and not succeeded and attempted_at > now() - interval '1 hour';
  if v_failures >= 10 then
    return jsonb_build_object('status', 'TOO_MANY_ATTEMPTS');
  end if;

  select * into v_code from public.pairing_codes where code = p_code;
  if not found or (v_code.used_at is not null and not v_code.reusable) then
    insert into public.pairing_attempts (user_id, succeeded) values (v_uid, false);
    return jsonb_build_object('status', 'INVALID_CODE');
  end if;
  if v_code.expires_at <= now() then
    insert into public.pairing_attempts (user_id, succeeded) values (v_uid, false);
    return jsonb_build_object('status', 'EXPIRED_CODE');
  end if;

  select * into v_existing from public.vehicles where id = p_vehicle_id;
  if found and v_existing.org_id <> v_code.org_id and v_existing.device_user_id is not null
     and v_existing.device_user_id <> v_uid then
    insert into public.pairing_attempts (user_id, succeeded) values (v_uid, false);
    return jsonb_build_object('status', 'VEHICLE_IN_OTHER_ORG');
  end if;

  -- 이 기기가 다른 차량 ID로 등록돼 있었다면 그 연결을 끊는다
  update public.vehicles set device_user_id = null where device_user_id = v_uid and id <> p_vehicle_id;

  insert into public.vehicles (id, org_id, vin, display_name, device_user_id, paired_at)
  values (p_vehicle_id, v_code.org_id, p_vin, coalesce(v_code.vehicle_name, p_vin, '차량'), v_uid, now())
  on conflict (id) do update set
    org_id = excluded.org_id,
    vin = coalesce(excluded.vin, public.vehicles.vin),
    display_name = coalesce(v_code.vehicle_name, public.vehicles.display_name),
    device_user_id = v_uid,
    paired_at = now(),
    default_driver_id = case when public.vehicles.org_id = excluded.org_id then public.vehicles.default_driver_id end;

  select default_driver_id into v_driver from public.vehicles where id = p_vehicle_id;
  if v_driver is null then
    insert into public.drivers (org_id, name) values (v_code.org_id, '기본 운전자') returning id into v_driver;
    update public.vehicles set default_driver_id = v_driver where id = p_vehicle_id;
  end if;

  select name into v_driver_name from public.drivers where id = v_driver;
  select name into v_org_name from public.organizations where id = v_code.org_id;

  if not v_code.reusable then
    update public.pairing_codes set used_at = now(), used_by_vehicle = p_vehicle_id where code = p_code;
  end if;
  insert into public.pairing_attempts (user_id, succeeded) values (v_uid, true);

  return jsonb_build_object(
    'status', 'OK',
    'org_id', v_code.org_id,
    'org_name', v_org_name,
    'vehicle_id', p_vehicle_id,
    'driver_id', v_driver,
    'driver_name', v_driver_name
  );
end;
$$;

-- 함수는 로그인한 사용자(익명 차량 계정 포함)만 부를 수 있다. 권한 검사는 함수 안에서 한다
revoke all on function public.has_org_role(uuid, public.app_role[]) from public, anon;
revoke all on function public.is_vehicle_device(text) from public, anon;
revoke all on function public.device_org() from public, anon;
revoke all on function public.is_driver_user(uuid) from public, anon;
revoke all on function public.issue_pairing_code(uuid, text) from public, anon;
revoke all on function public.pair_vehicle(text, text, text) from public, anon;
grant execute on function public.has_org_role(uuid, public.app_role[]) to authenticated;
grant execute on function public.is_vehicle_device(text) to authenticated;
grant execute on function public.device_org() to authenticated;
grant execute on function public.is_driver_user(uuid) to authenticated;
grant execute on function public.issue_pairing_code(uuid, text) to authenticated;
grant execute on function public.pair_vehicle(text, text, text) to authenticated;

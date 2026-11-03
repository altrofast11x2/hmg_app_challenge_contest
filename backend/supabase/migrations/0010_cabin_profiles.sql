-- 0010 공용차 운전자 맞춤과 차량 인계 (2026-11-03)
-- 1. 운전자: 부서, 숨김, 차량 맞춤 설정(온도·시트 열선·무드등 색). 승인된 직원은 차가 명단을 받을 때 운전자로 들어간다
-- 2. 운행: 운전자 이름·부서(국세청 서식 ④사용자 칸, 운행 당시 값), 업무 내용(음성 업무 일지)
-- 3. 차량 인계 상태: 운행이 끝날 때 차가 보고하는 마지막 상태 (창문·실내등·잠금·배터리, 차가 정리한 항목)
-- 쓰기는 모두 차량 기기 계정의 함수(device_*)로만 한다. 관리자는 웹에서 운전자를 추가·숨김 처리한다.

-- ─── 1. 운전자 ────────────────────────────────────────────────────────

alter table public.drivers
  add column department text check (department is null or char_length(department) between 1 and 40),
  add column archived boolean not null default false,
  add column comfort jsonb,
  add column comfort_updated_at timestamptz;

alter table public.drivers
  add constraint drivers_name_length check (char_length(name) between 1 and 40);

-- 직원 계정 하나에 운전자 한 명
create unique index drivers_org_user_unique on public.drivers (org_id, user_id) where user_id is not null;

-- ─── 2. 운행의 운전자 이름·부서, 업무 내용 ─────────────────────────────────

alter table public.trips
  add column driver_name text check (driver_name is null or char_length(driver_name) <= 40),
  add column driver_department text check (driver_department is null or char_length(driver_department) <= 40),
  add column work_note text check (work_note is null or char_length(work_note) <= 200);

-- 다른 조직의 운전자, 이미 지워진 운전자를 가리키면 운전자 연결만 비운다 (이름·부서는 운행 당시 값으로 남는다).
-- 예외를 내면 차에 쌓인 기록 전체의 업로드가 막히므로 비우는 쪽을 고른다
create function private.trips_driver_in_org()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.driver_id is not null
     and not exists (select 1 from public.drivers d where d.id = new.driver_id and d.org_id = new.org_id) then
    new.driver_id := null;
  end if;
  return new;
end;
$$;
revoke all on function private.trips_driver_in_org() from public, anon, authenticated;

create trigger trips_driver_in_org before insert or update of driver_id, org_id on public.trips
  for each row execute function private.trips_driver_in_org();

-- ─── 3. 차량 인계 상태 ──────────────────────────────────────────────────

create table public.vehicle_status (
  vehicle_id text primary key references public.vehicles (id) on delete cascade,
  org_id uuid not null references public.organizations (id) on delete cascade,
  reported_at_ms bigint not null,
  trip_id uuid,
  driver_id uuid references public.drivers (id) on delete set null,
  driver_name text check (driver_name is null or char_length(driver_name) <= 40),
  odometer_km numeric(10, 1),
  soc_percent int check (soc_percent between 0 and 100),
  -- 열려 있던 창문 (FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT)
  open_windows text[] not null default '{}',
  doors_locked boolean,
  cabin_light_on boolean,
  -- 운전자가 차 화면에서 [한 번에 정리]로 처리한 항목 (WINDOWS, LIGHTS, DOORS)
  tidied text[] not null default '{}',
  updated_at timestamptz not null default now()
);
create index vehicle_status_org on public.vehicle_status (org_id);
create index vehicle_status_driver on public.vehicle_status (driver_id);
alter table public.vehicle_status enable row level security;
create policy vehicle_status_select on public.vehicle_status for select to authenticated
  using (
    private.has_org_role(org_id, '{admin,viewer}')
    or private.is_vehicle_member(vehicle_id)
    or private.is_vehicle_device(vehicle_id)
  );
-- 쓰기 정책 없음: device_report_status 함수로만 쓴다

-- ─── 4. 차량 기기가 부르는 함수 ─────────────────────────────────────────

-- 이 차에서 고를 수 있는 운전자 명단.
-- 승인된 직원 중 아직 운전자가 아닌 사람은 여기서 운전자로 만든다. 내보낸 직원(가입 신청이 REJECTED)과 숨긴 운전자는 뺀다.
-- 이 차에 배정된 직원, 이 차를 최근에 운전한 사람 순으로 50명까지
create function public.device_driver_roster()
returns table (id uuid, name text, department text, comfort jsonb, assigned boolean, is_default boolean)
language plpgsql security definer set search_path = public as $$
#variable_conflict use_column
declare
  v_vehicle public.vehicles;
begin
  select * into v_vehicle from public.vehicles where device_user_id = auth.uid();
  if v_vehicle.id is null then
    raise exception 'NOT_PAIRED' using errcode = '42501';
  end if;

  insert into public.drivers (org_id, name, department, user_id)
  select r.org_id, left(r.name, 40), left(r.department, 40), r.user_id
  from public.member_requests r
  where r.org_id = v_vehicle.org_id
    and r.status = 'APPROVED'
    and not exists (select 1 from public.drivers d where d.org_id = r.org_id and d.user_id = r.user_id)
  on conflict do nothing;

  return query
  with roster as (
    select d.id, d.name, d.department, d.comfort,
           exists (
             select 1 from public.vehicle_members vm where vm.vehicle_id = v_vehicle.id and vm.user_id = d.user_id
           ) as assigned,
           d.id is not distinct from v_vehicle.default_driver_id as is_default,
           (select max(t.started_at_ms) from public.trips t where t.vehicle_id = v_vehicle.id and t.driver_id = d.id) as last_used
    from public.drivers d
    where d.org_id = v_vehicle.org_id
      and not d.archived
      and (
        d.user_id is null
        or exists (
          select 1 from public.member_requests r
          where r.user_id = d.user_id and r.org_id = d.org_id and r.status = 'APPROVED'
        )
      )
  )
  select r.id, r.name, r.department, r.comfort, r.assigned, r.is_default
  from roster r
  order by r.assigned desc, r.last_used desc nulls last, r.is_default, r.name
  limit 50;
end;
$$;

-- 운전자의 맞춤 설정을 저장한다. 차는 운행이 끝날 때 그 운전자가 쓰던 값을 읽어 보낸다.
-- 알려진 항목·범위만 남긴다: tempC 15~32, seatHeat -3~3 (음수는 통풍), ambientColor "#RRGGBB"
create function public.device_save_comfort(p_driver uuid, p_comfort jsonb)
returns boolean language plpgsql security definer set search_path = public as $$
declare
  v_org uuid := private.device_org();
  v_clean jsonb;
begin
  if v_org is null then
    raise exception 'NOT_PAIRED' using errcode = '42501';
  end if;
  if p_comfort is null or jsonb_typeof(p_comfort) <> 'object' then
    raise exception 'INVALID_COMFORT' using errcode = '22023';
  end if;
  v_clean := jsonb_strip_nulls(jsonb_build_object(
    'tempC', case
      when jsonb_typeof(p_comfort -> 'tempC') = 'number' and (p_comfort ->> 'tempC')::numeric between 15 and 32
      then round((p_comfort ->> 'tempC')::numeric, 1) end,
    'seatHeat', case
      when jsonb_typeof(p_comfort -> 'seatHeat') = 'number' and (p_comfort ->> 'seatHeat')::numeric between -3 and 3
      then (p_comfort ->> 'seatHeat')::numeric::int end,
    'ambientColor', case
      when p_comfort ->> 'ambientColor' ~ '^#[0-9A-Fa-f]{6}$' then upper(p_comfort ->> 'ambientColor') end
  ));
  update public.drivers
  set comfort = v_clean, comfort_updated_at = now()
  where id = p_driver and org_id = v_org and not archived;
  return found;
end;
$$;

-- 운행이 끝날 때의 차량 상태를 보고한다. 더 늦게 보고된 상태만 남긴다
create function public.device_report_status(p_status jsonb)
returns boolean language plpgsql security definer set search_path = public as $$
declare
  v_vehicle public.vehicles;
  v_driver uuid;
  v_windows text[];
  v_tidied text[];
begin
  select * into v_vehicle from public.vehicles where device_user_id = auth.uid();
  if v_vehicle.id is null then
    raise exception 'NOT_PAIRED' using errcode = '42501';
  end if;
  if p_status is null or jsonb_typeof(p_status) <> 'object' or jsonb_typeof(p_status -> 'reported_at_ms') <> 'number' then
    raise exception 'INVALID_STATUS' using errcode = '22023';
  end if;

  select d.id into v_driver from public.drivers d
  where d.org_id = v_vehicle.org_id
    and d.id::text = (p_status ->> 'driver_id')
    and (p_status ->> 'driver_id') ~ '^[0-9a-fA-F-]{36}$';

  select coalesce(array_agg(distinct w), '{}') into v_windows
  from jsonb_array_elements_text(coalesce(p_status -> 'open_windows', '[]'::jsonb)) w
  where w in ('FRONT_LEFT', 'FRONT_RIGHT', 'REAR_LEFT', 'REAR_RIGHT');

  select coalesce(array_agg(distinct t), '{}') into v_tidied
  from jsonb_array_elements_text(coalesce(p_status -> 'tidied', '[]'::jsonb)) t
  where t in ('WINDOWS', 'LIGHTS', 'DOORS');

  insert into public.vehicle_status as s (
    vehicle_id, org_id, reported_at_ms, trip_id, driver_id, driver_name,
    odometer_km, soc_percent, open_windows, doors_locked, cabin_light_on, tidied, updated_at
  ) values (
    v_vehicle.id,
    v_vehicle.org_id,
    (p_status ->> 'reported_at_ms')::bigint,
    case when (p_status ->> 'trip_id') ~ '^[0-9a-fA-F-]{36}$' then (p_status ->> 'trip_id')::uuid end,
    v_driver,
    left(p_status ->> 'driver_name', 40),
    case when jsonb_typeof(p_status -> 'odometer_km') = 'number' then round((p_status ->> 'odometer_km')::numeric, 1) end,
    case when jsonb_typeof(p_status -> 'soc_percent') = 'number' and (p_status ->> 'soc_percent')::numeric between 0 and 100
      then (p_status ->> 'soc_percent')::numeric::int end,
    v_windows,
    case when jsonb_typeof(p_status -> 'doors_locked') = 'boolean' then (p_status ->> 'doors_locked')::boolean end,
    case when jsonb_typeof(p_status -> 'cabin_light_on') = 'boolean' then (p_status ->> 'cabin_light_on')::boolean end,
    v_tidied,
    now()
  )
  on conflict (vehicle_id) do update set
    org_id = excluded.org_id,
    reported_at_ms = excluded.reported_at_ms,
    trip_id = excluded.trip_id,
    driver_id = excluded.driver_id,
    driver_name = excluded.driver_name,
    odometer_km = excluded.odometer_km,
    soc_percent = excluded.soc_percent,
    open_windows = excluded.open_windows,
    doors_locked = excluded.doors_locked,
    cabin_light_on = excluded.cabin_light_on,
    tidied = excluded.tidied,
    updated_at = now()
  where excluded.reported_at_ms >= s.reported_at_ms or s.org_id <> excluded.org_id;
  return true;
end;
$$;

revoke all on function public.device_driver_roster() from public, anon;
revoke all on function public.device_save_comfort(uuid, jsonb) from public, anon;
revoke all on function public.device_report_status(jsonb) from public, anon;
grant execute on function public.device_driver_roster() to authenticated;
grant execute on function public.device_save_comfort(uuid, jsonb) to authenticated;
grant execute on function public.device_report_status(jsonb) to authenticated;

-- ─── 5. 휴대폰으로 받기: 운전자 이름·부서, 업무 내용 추가 ─────────────────────

create or replace function public.shared_logbook(p_token text)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_link public.share_links;
  v_vehicle public.vehicles;
  v_org public.organizations;
begin
  select * into v_link from public.share_links
  where token_hash = encode(extensions.digest(coalesce(p_token, ''), 'sha256'), 'hex');
  if v_link.token_hash is null or v_link.expires_at < now() then
    return jsonb_build_object('status', 'EXPIRED');
  end if;
  select * into v_vehicle from public.vehicles where id = v_link.vehicle_id;
  select * into v_org from public.organizations where id = v_vehicle.org_id;
  return jsonb_build_object(
    'status', 'OK',
    'expires_at', v_link.expires_at,
    'from_ms', v_link.from_ms,
    'to_ms', v_link.to_ms,
    'org', jsonb_build_object('name', v_org.name, 'business_number', v_org.business_number),
    'vehicle', jsonb_build_object(
      'id', v_vehicle.id, 'display_name', v_vehicle.display_name, 'vin', v_vehicle.vin,
      'model', v_vehicle.model, 'plate_number', v_vehicle.plate_number
    ),
    'drivers', coalesce((
      select jsonb_object_agg(d.id, d.name) from public.drivers d
      where d.id in (
        select t.driver_id from public.trips t
        where t.vehicle_id = v_link.vehicle_id and t.started_at_ms >= v_link.from_ms and t.started_at_ms < v_link.to_ms
      )
    ), '{}'::jsonb),
    'trips', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', t.id, 'vehicle_id', t.vehicle_id, 'driver_id', t.driver_id,
        'driver_name', t.driver_name, 'driver_department', t.driver_department,
        'started_at_ms', t.started_at_ms, 'ended_at_ms', t.ended_at_ms,
        'start_odometer_km', t.start_odometer_km, 'end_odometer_km', t.end_odometer_km,
        'visit_note', t.visit_note, 'work_note', t.work_note, 'purpose', t.purpose
      ) order by t.started_at_ms)
      from public.trips t
      where t.vehicle_id = v_link.vehicle_id and t.started_at_ms >= v_link.from_ms and t.started_at_ms < v_link.to_ms
    ), '[]'::jsonb)
  );
end;
$$;

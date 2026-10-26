-- Supabase 보안·성능 점검(advisors) 반영
-- 1. RLS 판단 함수를 API에 노출되지 않는 private 스키마로 옮긴다 (/rest/v1/rpc 로 직접 부를 수 없게)
-- 2. 정책은 로그인 사용자(authenticated)에게만 적용하고, auth.uid()는 행마다 다시 계산하지 않게 (select ...)로 감싼다
-- 3. 외래 키에 인덱스를 단다
-- pair_vehicle(차량 앱)과 issue_pairing_code(관리자 웹)는 원래 API로 부르는 함수라 public에 그대로 둔다.

create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated;

alter function public.has_org_role(uuid, public.app_role[]) set schema private;
alter function public.is_vehicle_device(text) set schema private;
alter function public.device_org() set schema private;
alter function public.is_driver_user(uuid) set schema private;

-- ─── 정책 다시 만들기 ──────────────────────────────────────────────────

drop policy organizations_select on public.organizations;
drop policy memberships_select on public.memberships;
drop policy drivers_select on public.drivers;
drop policy drivers_insert on public.drivers;
drop policy drivers_update on public.drivers;
drop policy drivers_delete on public.drivers;
drop policy vehicles_select on public.vehicles;
drop policy vehicles_update on public.vehicles;
drop policy vehicles_delete on public.vehicles;
drop policy pairing_codes_select on public.pairing_codes;
drop policy pairing_codes_delete on public.pairing_codes;
drop policy trips_select on public.trips;
drop policy trips_insert on public.trips;
drop policy trips_update on public.trips;
drop policy trip_changes_select on public.trip_changes;
drop policy trip_changes_insert on public.trip_changes;
drop policy trip_changes_update on public.trip_changes;
drop policy chain_heads_select on public.chain_heads;
drop policy chain_heads_insert on public.chain_heads;
drop policy chain_heads_update on public.chain_heads;

-- 조직: 구성원과 그 조직의 차량이 볼 수 있다
create policy organizations_select on public.organizations for select to authenticated
  using (private.has_org_role(id, '{admin,driver,viewer}') or id = (select private.device_org()));

-- 구성원: 자기 행, 관리자는 조직 전체
create policy memberships_select on public.memberships for select to authenticated
  using (user_id = (select auth.uid()) or private.has_org_role(org_id, '{admin}'));

-- 운전자: 구성원·차량이 보고, 관리자가 추가·수정·삭제한다
create policy drivers_select on public.drivers for select to authenticated
  using (private.has_org_role(org_id, '{admin,driver,viewer}') or org_id = (select private.device_org()));
create policy drivers_insert on public.drivers for insert to authenticated
  with check (private.has_org_role(org_id, '{admin}'));
create policy drivers_update on public.drivers for update to authenticated
  using (private.has_org_role(org_id, '{admin}')) with check (private.has_org_role(org_id, '{admin}'));
create policy drivers_delete on public.drivers for delete to authenticated
  using (private.has_org_role(org_id, '{admin}'));

-- 차량: 등록은 pair_vehicle 함수로만. 관리자가 이름·기본 운전자를 고치고 삭제(연결 해제)한다
create policy vehicles_select on public.vehicles for select to authenticated
  using (private.has_org_role(org_id, '{admin,driver,viewer}') or device_user_id = (select auth.uid()));
create policy vehicles_update on public.vehicles for update to authenticated
  using (private.has_org_role(org_id, '{admin}')) with check (private.has_org_role(org_id, '{admin}'));
create policy vehicles_delete on public.vehicles for delete to authenticated
  using (private.has_org_role(org_id, '{admin}'));

-- 연결 코드: 발급은 issue_pairing_code 함수로만. 관리자가 보고 지운다
create policy pairing_codes_select on public.pairing_codes for select to authenticated
  using (private.has_org_role(org_id, '{admin}'));
create policy pairing_codes_delete on public.pairing_codes for delete to authenticated
  using (private.has_org_role(org_id, '{admin}'));

-- 운행: 관리자·열람자는 조직 전체, 운전자는 자기 운행, 차량은 자기 운행을 본다. 쓰기는 차량만 (관리자는 고칠 수 없다)
create policy trips_select on public.trips for select to authenticated
  using (
    private.has_org_role(org_id, '{admin,viewer}')
    or (private.has_org_role(org_id, '{driver}') and private.is_driver_user(driver_id))
    or private.is_vehicle_device(vehicle_id)
  );
create policy trips_insert on public.trips for insert to authenticated
  with check (private.is_vehicle_device(vehicle_id) and org_id = (select private.device_org()));
create policy trips_update on public.trips for update to authenticated
  using (private.is_vehicle_device(vehicle_id))
  with check (private.is_vehicle_device(vehicle_id) and org_id = (select private.device_org()));

create policy trip_changes_select on public.trip_changes for select to authenticated
  using (
    private.has_org_role(org_id, '{admin,viewer}')
    or exists (select 1 from public.trips t where t.id = trip_id and private.is_driver_user(t.driver_id))
    or private.is_vehicle_device(vehicle_id)
  );
create policy trip_changes_insert on public.trip_changes for insert to authenticated
  with check (private.is_vehicle_device(vehicle_id) and org_id = (select private.device_org()));
create policy trip_changes_update on public.trip_changes for update to authenticated
  using (private.is_vehicle_device(vehicle_id))
  with check (private.is_vehicle_device(vehicle_id) and org_id = (select private.device_org()));

create policy chain_heads_select on public.chain_heads for select to authenticated
  using (private.has_org_role(org_id, '{admin,viewer}') or private.is_vehicle_device(vehicle_id));
create policy chain_heads_insert on public.chain_heads for insert to authenticated
  with check (private.is_vehicle_device(vehicle_id) and org_id = (select private.device_org()));
create policy chain_heads_update on public.chain_heads for update to authenticated
  using (private.is_vehicle_device(vehicle_id))
  with check (private.is_vehicle_device(vehicle_id) and org_id = (select private.device_org()));

-- ─── 함수 본문이 옮긴 함수를 가리키게 고친다 ─────────────────────────────

create or replace function public.issue_pairing_code(p_org uuid, p_vehicle_name text default null)
returns table (code text, expires_at timestamptz)
language plpgsql security definer set search_path = public as $$
declare
  v_code text;
  v_bytes bytea;
  v_expires timestamptz := now() + interval '10 minutes';
begin
  if not private.has_org_role(p_org, '{admin}') then
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

-- ─── 외래 키 인덱스 ────────────────────────────────────────────────────

create index chain_heads_org on public.chain_heads (org_id);
create index drivers_org on public.drivers (org_id);
create index drivers_user on public.drivers (user_id);
create index memberships_org on public.memberships (org_id);
create index pairing_codes_org on public.pairing_codes (org_id);
create index pairing_codes_created_by on public.pairing_codes (created_by);
create index trip_changes_org on public.trip_changes (org_id);
create index trip_changes_trip on public.trip_changes (trip_id);
create index trips_driver on public.trips (driver_id);
create index vehicles_org on public.vehicles (org_id);
create index vehicles_default_driver on public.vehicles (default_driver_id);

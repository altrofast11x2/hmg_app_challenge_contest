-- 2026-10-27 점검에서 찾은 버그 고침
-- 1. 앱에서 동의를 철회하면 서버의 차량도 연결 해제한다 (전에는 차 안 기록만 지워지고 서버는 계속 "연결됨"이었다)
-- 2. 휴대폰으로 받기 링크가 조직 전체 운전자 이름을 넘기던 것을, 그 기간 운행에 나온 운전자 이름만 넘기게 한다
-- 3. 직원 가입 코드 틀린 시도 제한을 모든 기업 공통(1시간 60번)에서 접속 주소별(1시간 20번)로 바꾼다.
--    전에는 누가 틀린 코드를 계속 넣으면 다른 기업의 정상 가입도 막혔다. 주소는 sha256으로만 저장한다

-- ─── 1. 차량이 스스로 연결 해제 ──────────────────────────────────────────
create function public.unpair_own_vehicle()
returns int language plpgsql security definer set search_path = public as $$
declare
  v_count int;
begin
  update public.vehicles set device_user_id = null where device_user_id = (select auth.uid());
  get diagnostics v_count = row_count;
  return v_count;
end;
$$;
revoke all on function public.unpair_own_vehicle() from public, anon;
grant execute on function public.unpair_own_vehicle() to authenticated;

-- ─── 2. 휴대폰으로 받기: 그 기간 운행의 운전자 이름만 ─────────────────────────
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
        'started_at_ms', t.started_at_ms, 'ended_at_ms', t.ended_at_ms,
        'start_odometer_km', t.start_odometer_km, 'end_odometer_km', t.end_odometer_km,
        'visit_note', t.visit_note, 'purpose', t.purpose
      ) order by t.started_at_ms)
      from public.trips t
      where t.vehicle_id = v_link.vehicle_id and t.started_at_ms >= v_link.from_ms and t.started_at_ms < v_link.to_ms
    ), '[]'::jsonb)
  );
end;
$$;

-- ─── 3. 가입 코드 틀린 시도: 접속 주소별 제한 ────────────────────────────────
alter table public.join_attempts add column client_hash text not null default 'unknown';
create index join_attempts_client on public.join_attempts (client_hash, attempted_at);

-- 요청한 쪽의 주소(API 게이트웨이가 넘기는 x-forwarded-for의 첫 값)를 sha256으로. 알 수 없으면 'unknown'
create function private.request_client_hash()
returns text language sql stable set search_path = public as $$
  select coalesce(
    encode(extensions.digest(nullif(trim(split_part(
      coalesce(nullif(current_setting('request.headers', true), ''), '{}')::json ->> 'x-forwarded-for', ',', 1)), ''), 'sha256'), 'hex'),
    'unknown'
  );
$$;
revoke all on function private.request_client_hash() from public, anon, authenticated;

create or replace function public.member_sign_up(
  p_join_code text,
  p_email text,
  p_password text,
  p_name text,
  p_department text,
  p_employee_no text,
  p_phone text
)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_org public.organizations;
  v_name text := trim(coalesce(p_name, ''));
  v_department text := trim(coalesce(p_department, ''));
  v_phone text := trim(coalesce(p_phone, ''));
  v_email text := lower(trim(coalesce(p_email, '')));
  v_client text := private.request_client_hash();
  v_user uuid;
begin
  delete from public.join_attempts where attempted_at < now() - interval '1 day';
  if (select count(*) from public.join_attempts
      where client_hash = v_client and attempted_at > now() - interval '1 hour') >= 20 then
    return jsonb_build_object('status', 'TOO_MANY');
  end if;
  select * into v_org from public.organizations where join_code = upper(trim(coalesce(p_join_code, '')));
  if v_org.id is null then
    insert into public.join_attempts (client_hash) values (v_client);
    return jsonb_build_object('status', 'INVALID_CODE');
  end if;
  if (select count(*) from public.member_requests where org_id = v_org.id and created_at > now() - interval '1 hour') >= 30 then
    return jsonb_build_object('status', 'TOO_MANY');
  end if;
  if v_name = '' or char_length(v_name) > 40 then
    return jsonb_build_object('status', 'INVALID_NAME');
  end if;
  if v_department = '' or char_length(v_department) > 40 then
    return jsonb_build_object('status', 'INVALID_DEPARTMENT');
  end if;
  if v_phone !~ '^[0-9+\- ]{9,20}$' then
    return jsonb_build_object('status', 'INVALID_PHONE');
  end if;
  if v_email !~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$' or char_length(v_email) > 120 then
    return jsonb_build_object('status', 'INVALID_EMAIL');
  end if;
  if char_length(coalesce(p_password, '')) < 8 then
    return jsonb_build_object('status', 'WEAK_PASSWORD');
  end if;
  v_user := private.create_auth_user(v_email, p_password, v_name);
  if v_user is null then
    return jsonb_build_object('status', 'EMAIL_TAKEN');
  end if;
  insert into public.member_requests (org_id, user_id, name, department, employee_no, phone)
  values (v_org.id, v_user, v_name, v_department, nullif(trim(coalesce(p_employee_no, '')), ''), v_phone);
  return jsonb_build_object('status', 'OK', 'org_name', v_org.name);
end;
$$;

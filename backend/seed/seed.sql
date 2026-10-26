-- 시연용 데모 조직, 관리자 권한, 연결 코드를 만든다 (seed.mjs와 같은 결과, secret 키 없이 쓰는 방법).
-- SQL Editor나 Supabase MCP(execute_sql)로 실행한다. 여러 번 실행해도 같은 결과가 된다.
-- 데모 데이터는 backend/seed에만 있고 APK에는 넣지 않는다.
--
-- 먼저 대시보드 Authentication > Users > Add user > Create new user 로 관리자 계정을 만든다
-- (Auto Confirm User 체크 → 실제 메일이 가지 않는다). 그다음 아래 세 값을 바꿔서 실행한다.
do $$
declare
  v_org_name text := 'AltroStudio 데모';
  v_admin_email text := 'admin@email.com';
  v_code text := '<6자리 숫자>';  -- 심사·시연용 연결 코드 (여러 번 쓸 수 있고 2027-02-28까지 유효)
  v_admin uuid;
  v_org uuid;
begin
  if v_code !~ '^[0-9]{6}$' then
    raise exception '연결 코드는 6자리 숫자여야 합니다';
  end if;

  -- 1. 관리자 계정 (대시보드에서 만든 계정을 찾는다)
  select id into v_admin from auth.users where email = v_admin_email;
  if v_admin is null then
    raise exception '관리자 계정 %이 없습니다. Authentication > Users에서 먼저 만드세요', v_admin_email;
  end if;

  -- 2. 데모 조직 (이름으로 찾고 없으면 만든다)
  select id into v_org from public.organizations where name = v_org_name order by created_at limit 1;
  if v_org is null then
    insert into public.organizations (name) values (v_org_name) returning id into v_org;
  end if;

  insert into public.memberships (user_id, org_id, role, display_name)
  values (v_admin, v_org, 'admin', '데모 관리자')
  on conflict (user_id, org_id) do update set role = excluded.role, display_name = excluded.display_name;

  -- 3. 시연용 연결 코드 (여러 번 사용 가능)
  insert into public.pairing_codes (code, org_id, vehicle_name, expires_at, reusable, used_at)
  values (v_code, v_org, '데모 차량', '2027-02-28T23:59:59+09:00', true, null)
  on conflict (code) do update
    set org_id = excluded.org_id, vehicle_name = excluded.vehicle_name,
        expires_at = excluded.expires_at, reusable = true, used_at = null;

  raise notice '데모 조직: % / 관리자: % / 연결 코드: %', v_org_name, v_admin_email, v_code;
end $$;

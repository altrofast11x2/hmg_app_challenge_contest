-- 시연용 계정과 데모 기업을 만든다 (migrations 0001~0004 적용 뒤). SQL Editor나 Supabase MCP(execute_sql)로 실행한다.
-- 여러 번 실행해도 같은 결과가 된다. 데모 데이터는 backend/seed에만 있고 APK에는 넣지 않는다.
-- 실제 값(비밀번호, 연결 코드)은 커밋하지 않는 backend/.env에 적어 두고, 실행할 때 아래 <...> 칸만 바꾼다.
--
-- 계정 2종 (모두 이메일 확인 처리된 상태로 만들어 실제 메일은 가지 않는다)
--   개발자(서비스 운영): 관리자 웹에서 기업을 등록하고 기업 관리자 계정을 만든다. 운행 기록은 볼 수 없다
--   기업 관리자: 데모 기업의 차량 연결 코드 발급, 운행기록부 열람
do $$
declare
  v_dev_email text := 'altrostudio@altro.com';        -- 웹 로그인 아이디 AltroStudio@altro.com (대소문자 무관)
  v_dev_password text := '<개발자 비밀번호>';
  v_org_name text := 'AltroStudio 데모';
  v_admin_email text := 'demo@altro.com';
  v_admin_password text := '<기업 관리자 비밀번호>';
  v_code text := '<6자리 숫자>';                       -- 심사·시연용 연결 코드 (여러 번 쓸 수 있고 2027-02-28까지 유효)
  v_dev uuid;
  v_admin uuid;
  v_org uuid;
begin
  if v_code !~ '^[0-9]{6}$' then
    raise exception '연결 코드는 6자리 숫자여야 합니다';
  end if;
  if length(v_dev_password) < 8 or length(v_admin_password) < 8 or v_dev_password like '<%' or v_admin_password like '<%' then
    raise exception '비밀번호 칸을 8자 이상으로 채우세요';
  end if;

  -- 1. 개발자 계정
  select id into v_dev from auth.users where email = v_dev_email;
  if v_dev is null then
    v_dev := private.create_auth_user(v_dev_email, v_dev_password, 'AltroStudio (개발자)');
  else
    update auth.users set encrypted_password = extensions.crypt(v_dev_password, extensions.gen_salt('bf')), updated_at = now()
    where id = v_dev;
  end if;
  insert into public.developers (user_id) values (v_dev) on conflict do nothing;

  -- 2. 데모 기업 (이름으로 찾고 없으면 만든다)
  select id into v_org from public.organizations where name = v_org_name order by created_at limit 1;
  if v_org is null then
    insert into public.organizations (name) values (v_org_name) returning id into v_org;
  end if;
  update public.organizations
  set representative = '데모 대표', contact_name = '데모 담당자', contact_email = v_admin_email, admin_email = v_admin_email, created_by = v_dev
  where id = v_org;

  -- 3. 기업 관리자 계정
  select id into v_admin from auth.users where email = v_admin_email;
  if v_admin is null then
    v_admin := private.create_auth_user(v_admin_email, v_admin_password, v_org_name || ' 관리자');
  else
    update auth.users set encrypted_password = extensions.crypt(v_admin_password, extensions.gen_salt('bf')), updated_at = now()
    where id = v_admin;
  end if;
  insert into public.memberships (user_id, org_id, role, display_name)
  values (v_admin, v_org, 'admin', v_org_name || ' 관리자')
  on conflict (user_id, org_id) do update set role = excluded.role, display_name = excluded.display_name;

  -- 4. 시연용 연결 코드 (여러 번 사용 가능)
  insert into public.pairing_codes (code, org_id, vehicle_name, expires_at, reusable, used_at)
  values (v_code, v_org, '데모 차량', '2027-02-28T23:59:59+09:00', true, null)
  on conflict (code) do update
    set org_id = excluded.org_id, vehicle_name = excluded.vehicle_name,
        expires_at = excluded.expires_at, reusable = true, used_at = null;

  raise notice '개발자: % / 기업: % (관리자 %) / 연결 코드: %', v_dev_email, v_org_name, v_admin_email, v_code;
end $$;

-- 시연용 데모 조직, 관리자 계정, 연결 코드를 만든다 (seed.mjs와 같은 결과, secret 키 없이 쓰는 방법).
-- SQL Editor나 Supabase MCP(execute_sql)로 실행한다. 여러 번 실행해도 같은 결과가 된다.
-- 데모 데이터는 backend/seed에만 있고 APK에는 넣지 않는다. 실제 값은 backend/.env(커밋 금지)에 적어 둔다.
--
-- 관리자 계정
--   - v_admin_password에 비밀번호를 넣으면 계정을 만든다(이미 있으면 비밀번호만 바꾼다).
--     이메일 확인 처리된 계정으로 만들어서 실제 메일은 가지 않는다.
--   - 대시보드 Authentication > Users에서 직접 만들었다면 '<비밀번호>'를 그대로 둔다.
do $$
declare
  v_org_name text := 'AltroStudio 데모';
  -- 관리자 웹은 아이디 AltroStudio로 로그인한다. 웹이 아이디를 이 주소로 바꾼다 (web/js/api.js loginEmail)
  v_admin_email text := 'altrostudio@drivelog.invalid';
  v_admin_display text := 'AltroStudio';
  v_admin_password text := '<비밀번호>';
  v_code text := '<6자리 숫자>';  -- 심사·시연용 연결 코드 (여러 번 쓸 수 있고 2027-02-28까지 유효)
  v_admin uuid;
  v_org uuid;
begin
  if v_code !~ '^[0-9]{6}$' then
    raise exception '연결 코드는 6자리 숫자여야 합니다';
  end if;

  -- 1. 관리자 계정
  select id into v_admin from auth.users where email = v_admin_email;
  if v_admin_password <> '<비밀번호>' then
    if length(v_admin_password) < 6 then
      raise exception '비밀번호는 6자 이상이어야 합니다';
    end if;
    if v_admin is null then
      v_admin := gen_random_uuid();
      insert into auth.users (
        instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
        raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
        confirmation_token, recovery_token, email_change_token_new, email_change
      ) values (
        '00000000-0000-0000-0000-000000000000', v_admin, 'authenticated', 'authenticated', v_admin_email,
        extensions.crypt(v_admin_password, extensions.gen_salt('bf')), now(),
        '{"provider": "email", "providers": ["email"]}', jsonb_build_object('display_name', v_admin_display), now(), now(),
        '', '', '', ''
      );
      insert into auth.identities (provider_id, user_id, identity_data, provider, last_sign_in_at, created_at, updated_at)
      values (
        v_admin::text, v_admin,
        jsonb_build_object('sub', v_admin::text, 'email', v_admin_email, 'email_verified', true),
        'email', now(), now(), now()
      );
    else
      update auth.users
      set encrypted_password = extensions.crypt(v_admin_password, extensions.gen_salt('bf')),
          raw_user_meta_data = coalesce(raw_user_meta_data, '{}'::jsonb) || jsonb_build_object('display_name', v_admin_display),
          updated_at = now()
      where id = v_admin;
    end if;
  end if;
  if v_admin is null then
    raise exception '관리자 계정 %이 없습니다. v_admin_password를 채우거나 Authentication > Users에서 먼저 만드세요', v_admin_email;
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

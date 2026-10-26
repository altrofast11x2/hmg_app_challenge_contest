-- 개발자(서비스 운영) 계정과 기업 등록
-- 개발자는 관리자 웹에서 기업(조직) 정보를 적어 등록하고, 그 기업의 관리자 계정을 함께 만든다.
-- 개발자는 기업 목록과 차량 수만 보고, 기업의 운행 기록은 볼 수 없다.

-- 기업 정보
alter table public.organizations
  add column business_number text,   -- 사업자등록번호
  add column representative text,    -- 대표자
  add column address text,
  add column contact_name text,      -- 담당자
  add column contact_phone text,
  add column contact_email text,
  add column admin_email text,       -- 처음 만든 기업 관리자 아이디 (개발자 목록 표시용)
  add column created_by uuid references auth.users (id) on delete set null;

create index organizations_created_by on public.organizations (created_by);

-- 개발자 계정 목록 (seed로만 추가한다)
create table public.developers (
  user_id uuid primary key references auth.users (id) on delete cascade,
  created_at timestamptz not null default now()
);
alter table public.developers enable row level security;

-- 자기 행만 볼 수 있다 (웹이 개발자인지 확인할 때)
create policy developers_select on public.developers for select to authenticated
  using (user_id = (select auth.uid()));

create function private.is_developer()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.developers d where d.user_id = auth.uid());
$$;
revoke all on function private.is_developer() from public, anon;
grant execute on function private.is_developer() to authenticated;

-- 개발자는 모든 기업, 구성원, 차량(수)을 볼 수 있다. 운행·분류 이력은 보지 않는다
create policy organizations_select_developer on public.organizations for select to authenticated
  using ((select private.is_developer()));
create policy memberships_select_developer on public.memberships for select to authenticated
  using ((select private.is_developer()));
create policy vehicles_select_developer on public.vehicles for select to authenticated
  using ((select private.is_developer()));

-- 로그인 계정 만들기 (이메일 확인 처리, 실제 메일은 보내지 않는다). 이미 있으면 null.
-- 외부(API)에서 부를 수 없고, create_organization과 seed만 쓴다
create function private.create_auth_user(p_email text, p_password text, p_display_name text)
returns uuid language plpgsql security definer set search_path = public as $$
declare
  v_user uuid := gen_random_uuid();
  v_email text := lower(trim(p_email));
begin
  if exists (select 1 from auth.users where email = v_email) then
    return null;
  end if;
  insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, recovery_token, email_change_token_new, email_change
  ) values (
    '00000000-0000-0000-0000-000000000000', v_user, 'authenticated', 'authenticated', v_email,
    extensions.crypt(p_password, extensions.gen_salt('bf')), now(),
    '{"provider": "email", "providers": ["email"]}', jsonb_build_object('display_name', p_display_name), now(), now(),
    '', '', '', ''
  );
  insert into auth.identities (provider_id, user_id, identity_data, provider, last_sign_in_at, created_at, updated_at)
  values (v_user::text, v_user, jsonb_build_object('sub', v_user::text, 'email', v_email, 'email_verified', true), 'email', now(), now(), now());
  return v_user;
end;
$$;
revoke all on function private.create_auth_user(text, text, text) from public, anon, authenticated;

-- 개발자가 기업을 등록하고 기업 관리자 계정을 만든다.
-- 결과 status: OK, INVALID_NAME, INVALID_EMAIL, WEAK_PASSWORD, EMAIL_TAKEN
create function public.create_organization(
  p_name text,
  p_business_number text,
  p_representative text,
  p_address text,
  p_contact_name text,
  p_contact_phone text,
  p_contact_email text,
  p_admin_email text,
  p_admin_password text
)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_name text := trim(coalesce(p_name, ''));
  v_admin_email text := lower(trim(coalesce(p_admin_email, '')));
  v_org uuid;
  v_admin uuid;
begin
  if not private.is_developer() then
    raise exception 'FORBIDDEN' using errcode = '42501';
  end if;
  if v_name = '' then
    return jsonb_build_object('status', 'INVALID_NAME');
  end if;
  if v_admin_email !~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$' then
    return jsonb_build_object('status', 'INVALID_EMAIL');
  end if;
  if length(coalesce(p_admin_password, '')) < 8 then
    return jsonb_build_object('status', 'WEAK_PASSWORD');
  end if;

  v_admin := private.create_auth_user(v_admin_email, p_admin_password, v_name || ' 관리자');
  if v_admin is null then
    return jsonb_build_object('status', 'EMAIL_TAKEN');
  end if;

  insert into public.organizations (
    name, business_number, representative, address, contact_name, contact_phone, contact_email, admin_email, created_by
  ) values (
    v_name, nullif(trim(p_business_number), ''), nullif(trim(p_representative), ''), nullif(trim(p_address), ''),
    nullif(trim(p_contact_name), ''), nullif(trim(p_contact_phone), ''), nullif(lower(trim(p_contact_email)), ''),
    v_admin_email, auth.uid()
  ) returning id into v_org;

  insert into public.memberships (user_id, org_id, role, display_name)
  values (v_admin, v_org, 'admin', v_name || ' 관리자');

  return jsonb_build_object('status', 'OK', 'org_id', v_org, 'admin_email', v_admin_email);
end;
$$;
revoke all on function public.create_organization(text, text, text, text, text, text, text, text, text) from public, anon;
grant execute on function public.create_organization(text, text, text, text, text, text, text, text, text) to authenticated;

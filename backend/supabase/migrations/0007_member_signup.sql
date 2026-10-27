-- 직원 회원가입: 회사 차를 쓰는 직원이 관리자 웹에 직접 가입해 자기가 쓰는 차량의 운행기록부를 받는다.
-- 아무나 가입하지 못하도록
--   1. 회사 관리자가 직원에게 알려 주는 기업 가입 코드가 있어야 신청할 수 있고
--   2. 이름·부서·사번·휴대폰을 적어 내며 (소속 확인용, 관리자에게 보인다)
--   3. 관리자가 승인하면서 그 직원이 쓰는 차량을 고른다. 승인 전에는 아무 기록도 볼 수 없다.
-- 직원(driver 역할)은 배정받은 차량의 운행만 본다 (전에는 조직의 모든 차량이 보였다).
-- 개발자는 기업을 삭제할 수 있다 (시험·해지한 기업 정리).

-- ─── 기업 가입 코드 ─────────────────────────────────────────────────────

-- 헷갈리는 글자(0 O 1 I L)를 뺀 8자리
create function private.new_join_code()
returns text language plpgsql volatile set search_path = public as $$
declare
  v_alphabet constant text := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
  v_bytes bytea;
  v_code text;
begin
  loop
    v_bytes := extensions.gen_random_bytes(8);
    v_code := '';
    for i in 0..7 loop
      v_code := v_code || substr(v_alphabet, (get_byte(v_bytes, i) % length(v_alphabet)) + 1, 1);
    end loop;
    exit when not exists (select 1 from public.organizations where join_code = v_code);
  end loop;
  return v_code;
end;
$$;
revoke all on function private.new_join_code() from public, anon, authenticated;

alter table public.organizations add column join_code text unique;
update public.organizations set join_code = private.new_join_code() where join_code is null;
alter table public.organizations alter column join_code set not null;
alter table public.organizations alter column join_code set default private.new_join_code();

-- ─── 가입 신청과 차량 배정 ────────────────────────────────────────────────

create table public.member_requests (
  id uuid primary key default gen_random_uuid(),
  org_id uuid not null references public.organizations (id) on delete cascade,
  user_id uuid not null unique references auth.users (id) on delete cascade,
  name text not null,
  department text not null,
  employee_no text,
  phone text not null,
  status text not null default 'PENDING' check (status in ('PENDING', 'APPROVED', 'REJECTED')),
  created_at timestamptz not null default now(),
  decided_at timestamptz,
  decided_by uuid references auth.users (id) on delete set null
);
create index member_requests_org on public.member_requests (org_id, status);
create index member_requests_decided_by on public.member_requests (decided_by);
alter table public.member_requests enable row level security;
create policy member_requests_select on public.member_requests for select to authenticated
  using (user_id = (select auth.uid()) or private.has_org_role(org_id, '{admin}'));

-- 직원이 쓰는 차량 (관리자가 승인할 때 고르고 나중에 바꾼다)
create table public.vehicle_members (
  vehicle_id text not null references public.vehicles (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  org_id uuid not null references public.organizations (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (vehicle_id, user_id)
);
create index vehicle_members_user on public.vehicle_members (user_id);
create index vehicle_members_org on public.vehicle_members (org_id);
alter table public.vehicle_members enable row level security;
create policy vehicle_members_select on public.vehicle_members for select to authenticated
  using (user_id = (select auth.uid()) or private.has_org_role(org_id, '{admin}'));

-- 잘못된 가입 코드 시도 (1시간에 60번이 넘으면 잠시 막는다)
create table public.join_attempts (
  id bigint generated always as identity primary key,
  attempted_at timestamptz not null default now()
);
create index join_attempts_time on public.join_attempts (attempted_at);
alter table public.join_attempts enable row level security;

create function private.is_vehicle_member(p_vehicle text)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.vehicle_members vm where vm.vehicle_id = p_vehicle and vm.user_id = auth.uid());
$$;
revoke all on function private.is_vehicle_member(text) from public, anon;
grant execute on function private.is_vehicle_member(text) to authenticated;

-- ─── 직원은 배정받은 차량만 본다 ──────────────────────────────────────────

drop policy vehicles_select on public.vehicles;
create policy vehicles_select on public.vehicles for select to authenticated
  using (
    private.has_org_role(org_id, '{admin,viewer}')
    or private.is_vehicle_member(id)
    or device_user_id = (select auth.uid())
  );

drop policy trips_select on public.trips;
create policy trips_select on public.trips for select to authenticated
  using (
    private.has_org_role(org_id, '{admin,viewer}')
    or (private.has_org_role(org_id, '{driver}') and private.is_driver_user(driver_id))
    or private.is_vehicle_member(vehicle_id)
    or private.is_vehicle_device(vehicle_id)
  );

drop policy trip_changes_select on public.trip_changes;
create policy trip_changes_select on public.trip_changes for select to authenticated
  using (
    private.has_org_role(org_id, '{admin,viewer}')
    or exists (select 1 from public.trips t where t.id = trip_id and private.is_driver_user(t.driver_id))
    or private.is_vehicle_member(vehicle_id)
    or private.is_vehicle_device(vehicle_id)
  );

-- ─── 직원 가입 신청 (로그인 없이) ─────────────────────────────────────────
-- status: OK, INVALID_CODE, INVALID_NAME, INVALID_DEPARTMENT, INVALID_PHONE, INVALID_EMAIL, WEAK_PASSWORD, EMAIL_TAKEN, TOO_MANY
create function public.member_sign_up(
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
  v_user uuid;
begin
  if (select count(*) from public.join_attempts where attempted_at > now() - interval '1 hour') >= 60 then
    return jsonb_build_object('status', 'TOO_MANY');
  end if;
  select * into v_org from public.organizations where join_code = upper(trim(coalesce(p_join_code, '')));
  if v_org.id is null then
    insert into public.join_attempts default values;
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
revoke all on function public.member_sign_up(text, text, text, text, text, text, text) from public;
grant execute on function public.member_sign_up(text, text, text, text, text, text, text) to anon, authenticated;

-- ─── 관리자: 승인·거절, 차량 배정, 직원 내보내기, 가입 코드 바꾸기 ─────────────

-- 직원이 쓰는 차량을 바꾼다 (그 조직의 차량만)
create function public.set_member_vehicles(p_user uuid, p_org uuid, p_vehicle_ids text[])
returns void language plpgsql security definer set search_path = public as $$
begin
  if not private.has_org_role(p_org, '{admin}')
     or not exists (select 1 from public.memberships where user_id = p_user and org_id = p_org and role = 'driver') then
    raise exception 'FORBIDDEN' using errcode = '42501';
  end if;
  delete from public.vehicle_members where user_id = p_user and org_id = p_org;
  insert into public.vehicle_members (vehicle_id, user_id, org_id)
  select v.id, p_user, p_org from public.vehicles v where v.org_id = p_org and v.id = any (coalesce(p_vehicle_ids, '{}'));
end;
$$;

-- 승인하면 직원(driver)이 되고 고른 차량의 기록을 본다. 거절하면 아무것도 볼 수 없다
create function public.decide_member_request(p_request uuid, p_approve boolean, p_vehicle_ids text[] default '{}')
returns void language plpgsql security definer set search_path = public as $$
declare
  v_request public.member_requests;
begin
  select * into v_request from public.member_requests where id = p_request;
  if v_request.id is null or not private.has_org_role(v_request.org_id, '{admin}') then
    raise exception 'FORBIDDEN' using errcode = '42501';
  end if;
  update public.member_requests
  set status = case when p_approve then 'APPROVED' else 'REJECTED' end, decided_at = now(), decided_by = auth.uid()
  where id = p_request;
  if p_approve then
    insert into public.memberships (user_id, org_id, role, display_name)
    values (v_request.user_id, v_request.org_id, 'driver', v_request.name)
    on conflict (user_id, org_id) do nothing;
    perform public.set_member_vehicles(v_request.user_id, v_request.org_id, p_vehicle_ids);
  else
    delete from public.memberships where user_id = v_request.user_id and org_id = v_request.org_id and role = 'driver';
    delete from public.vehicle_members where user_id = v_request.user_id and org_id = v_request.org_id;
  end if;
end;
$$;

-- 직원을 내보낸다 (퇴사 등). 계정은 남지만 아무 기록도 볼 수 없다
create function public.remove_member(p_user uuid, p_org uuid)
returns void language plpgsql security definer set search_path = public as $$
begin
  if not private.has_org_role(p_org, '{admin}') then
    raise exception 'FORBIDDEN' using errcode = '42501';
  end if;
  delete from public.vehicle_members where user_id = p_user and org_id = p_org;
  delete from public.memberships where user_id = p_user and org_id = p_org and role = 'driver';
  update public.member_requests set status = 'REJECTED', decided_at = now(), decided_by = auth.uid()
  where user_id = p_user and org_id = p_org;
end;
$$;

-- 가입 코드가 밖으로 새면 새 코드로 바꾼다 (이미 가입한 직원은 그대로)
create function public.regenerate_join_code(p_org uuid)
returns text language plpgsql security definer set search_path = public as $$
declare
  v_code text := private.new_join_code();
begin
  if not private.has_org_role(p_org, '{admin}') then
    raise exception 'FORBIDDEN' using errcode = '42501';
  end if;
  update public.organizations set join_code = v_code where id = p_org;
  return v_code;
end;
$$;

-- ─── 개발자: 기업 삭제 ───────────────────────────────────────────────────
-- 기업과 그 차량·운행·연결 코드·가입 신청을 모두 지우고, 그 기업에만 속한 계정(관리자·직원·차량 기기)도 지운다.
-- 되돌릴 수 없으므로 웹에서 기업명을 한 번 더 입력해야 부를 수 있게 한다
create function public.delete_organization(p_org uuid, p_confirm_name text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_org public.organizations;
  v_users uuid[];
  v_vehicles int;
begin
  if not private.is_developer() then
    raise exception 'FORBIDDEN' using errcode = '42501';
  end if;
  select * into v_org from public.organizations where id = p_org;
  if v_org.id is null then
    return jsonb_build_object('status', 'NOT_FOUND');
  end if;
  if v_org.name <> trim(coalesce(p_confirm_name, '')) then
    return jsonb_build_object('status', 'NAME_MISMATCH');
  end if;
  -- 이 기업에만 속한 사람 계정(다른 기업 구성원·개발자는 남긴다)과 이 기업 차량의 기기 계정
  select coalesce(array_agg(distinct u), '{}') into v_users from (
    select m.user_id as u from public.memberships m
    where m.org_id = p_org
      and not exists (select 1 from public.memberships o where o.user_id = m.user_id and o.org_id <> p_org)
      and not exists (select 1 from public.developers d where d.user_id = m.user_id)
    union
    select r.user_id from public.member_requests r
    where r.org_id = p_org
      and not exists (select 1 from public.memberships o where o.user_id = r.user_id and o.org_id <> p_org)
      and not exists (select 1 from public.developers d where d.user_id = r.user_id)
    union
    select v.device_user_id from public.vehicles v where v.org_id = p_org and v.device_user_id is not null
  ) s;
  select count(*) into v_vehicles from public.vehicles where org_id = p_org;
  delete from public.organizations where id = p_org;
  delete from auth.users where id = any (v_users);
  return jsonb_build_object('status', 'OK', 'vehicles', v_vehicles, 'accounts', coalesce(array_length(v_users, 1), 0));
end;
$$;

revoke all on function public.decide_member_request(uuid, boolean, text[]) from public, anon;
revoke all on function public.set_member_vehicles(uuid, uuid, text[]) from public, anon;
revoke all on function public.remove_member(uuid, uuid) from public, anon;
revoke all on function public.regenerate_join_code(uuid) from public, anon;
revoke all on function public.delete_organization(uuid, text) from public, anon;
grant execute on function public.decide_member_request(uuid, boolean, text[]) to authenticated;
grant execute on function public.set_member_vehicles(uuid, uuid, text[]) to authenticated;
grant execute on function public.remove_member(uuid, uuid) to authenticated;
grant execute on function public.regenerate_join_code(uuid) to authenticated;
grant execute on function public.delete_organization(uuid, text) to authenticated;

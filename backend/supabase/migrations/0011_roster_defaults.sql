-- 0011 운전자 명단에서 다른 차량의 "기본 운전자" 빼기 (2026-11-04 에뮬레이터 시험에서 발견)
-- 차량을 연결할 때마다 그 차의 기본 운전자가 하나씩 생겨, 같은 기업에 차가 여러 대면 "기본 운전자"가 여러 번 보였다.
-- 이 차의 기본 운전자만 남긴다.

create or replace function public.device_driver_roster()
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
      and not exists (
        select 1 from public.vehicles v2
        where v2.default_driver_id = d.id and v2.id <> v_vehicle.id
      )
  )
  select r.id, r.name, r.department, r.comfort, r.assigned, r.is_default
  from roster r
  order by r.assigned desc, r.last_used desc nulls last, r.is_default, r.name
  limit 50;
end;
$$;

revoke all on function public.device_driver_roster() from public, anon;
grant execute on function public.device_driver_roster() to authenticated;

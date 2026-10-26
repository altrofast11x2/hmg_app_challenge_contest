-- 관리자 웹이 차량을 다룰 때 "고칠 수 없는 기록" 원칙을 지키도록 권한을 좁힌다
-- 1. 관리자는 차량 이름과 기본 운전자만 바꿀 수 있다.
--    (전에는 device_user_id도 바꿀 수 있어서, 관리자가 자기 계정을 차량으로 등록해 운행을 고칠 수 있었다)
-- 2. 차량 삭제를 막는다. 차량을 지우면 운행 기록이 함께 지워지기 때문이다.
--    대신 unpair_vehicle로 연결만 해제한다. 해제된 차량은 다른 조직의 코드로 다시 연결할 수 있다.

revoke update on public.vehicles from anon, authenticated;
grant update (display_name, default_driver_id) on public.vehicles to authenticated;

drop policy vehicles_delete on public.vehicles;

-- 관리자가 차량 연결을 해제한다. 운행 기록은 그대로 남는다
create function public.unpair_vehicle(p_vehicle text)
returns void
language plpgsql security definer set search_path = public as $$
declare
  v_org uuid;
begin
  select org_id into v_org from public.vehicles where id = p_vehicle;
  if v_org is null or not private.has_org_role(v_org, '{admin}') then
    raise exception 'FORBIDDEN' using errcode = '42501';
  end if;
  update public.vehicles set device_user_id = null where id = p_vehicle;
end;
$$;

revoke all on function public.unpair_vehicle(text) from public, anon;
grant execute on function public.unpair_vehicle(text) to authenticated;

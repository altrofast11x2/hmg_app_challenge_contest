-- 시연용 운전자 3명 (0010 적용 뒤). 데모 기업 "AltroStudio 데모"의 공용차를 번갈아 타는 직원이라고 가정한다.
-- 로그인 계정이 없는 운전자라 비밀번호가 없다. 맞춤 설정(온도·시트 열선·무드등)은 처음 값이고, 운행이 끝나면 차가 고쳐 쓴다.
-- 여러 번 실행해도 같은 결과가 된다. SQL Editor나 Supabase MCP(execute_sql)로 실행한다.
do $$
declare
  v_org uuid;
begin
  select id into v_org from public.organizations where name = 'AltroStudio 데모' order by created_at limit 1;
  if v_org is null then
    raise exception '데모 기업이 없습니다. seed.sql을 먼저 실행하세요';
  end if;

  insert into public.drivers (id, org_id, name, department, comfort, comfort_updated_at) values
    ('7d1f0a52-3c1e-4c55-9a51-1b2e7f0c0a01', v_org, '김민준', '영업1팀', '{"tempC": 21.5, "seatHeat": 0, "ambientColor": "#2F6BE0"}', now()),
    ('7d1f0a52-3c1e-4c55-9a51-1b2e7f0c0a02', v_org, '이서연', '기술지원팀', '{"tempC": 24.0, "seatHeat": 2, "ambientColor": "#F2B24C"}', now()),
    ('7d1f0a52-3c1e-4c55-9a51-1b2e7f0c0a03', v_org, '박지훈', '구매팀', '{"tempC": 22.0, "seatHeat": -1, "ambientColor": "#5CC6E8"}', now())
  on conflict (id) do update
    set org_id = excluded.org_id, name = excluded.name, department = excluded.department, archived = false;

  raise notice '데모 운전자 3명 준비 (기업 %)', v_org;
end $$;

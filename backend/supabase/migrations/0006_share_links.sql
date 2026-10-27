-- 휴대폰으로 받기: 차 화면의 QR 코드로 운전자가 자기 휴대폰에서 운행기록부를 연다 (로그인 없이, 10분 동안).
-- 차에는 메일·메신저 앱이 없어서, 차는 QR만 띄우고 저장·보내기는 휴대폰이 한다.
-- 조직에 연결한 차량만 쓸 수 있다 (연결하지 않은 차량의 기록은 차 밖으로 나가지 않는다).
-- 토큰 원문은 저장하지 않고 sha256만 저장한다. 링크로 볼 수 있는 것은 그 차량의 그 기간 운행뿐이다.

create table public.share_links (
  token_hash text primary key,
  vehicle_id text not null references public.vehicles (id) on delete cascade,
  from_ms bigint not null,
  to_ms bigint not null,
  expires_at timestamptz not null,
  created_at timestamptz not null default now(),
  check (to_ms > from_ms)
);

-- 정책이 없으므로 API로 직접 읽거나 쓸 수 없다. 아래 함수로만 다룬다
alter table public.share_links enable row level security;
create index share_links_vehicle on public.share_links (vehicle_id, created_at);

-- 차량(익명 로그인)이 자기 차량의 기간 링크를 만든다.
-- status: OK(token, expires_at), NOT_PAIRED, INVALID_PERIOD, TOO_MANY (한 차량이 1시간에 20개까지)
create function public.create_share_link(p_from_ms bigint, p_to_ms bigint)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_vehicle text;
  v_token text;
  v_expires timestamptz := now() + interval '10 minutes';
begin
  select id into v_vehicle from public.vehicles where device_user_id = (select auth.uid());
  if v_vehicle is null then
    return jsonb_build_object('status', 'NOT_PAIRED');
  end if;
  -- 운행기록부 기간은 366일까지 (하루 여유)
  if p_to_ms <= p_from_ms or p_to_ms - p_from_ms > 367::bigint * 86400000 then
    return jsonb_build_object('status', 'INVALID_PERIOD');
  end if;
  delete from public.share_links where expires_at < now() - interval '1 day';
  if (select count(*) from public.share_links where vehicle_id = v_vehicle and created_at > now() - interval '1 hour') >= 20 then
    return jsonb_build_object('status', 'TOO_MANY');
  end if;
  v_token := translate(encode(extensions.gen_random_bytes(24), 'base64'), '+/', '-_');
  insert into public.share_links (token_hash, vehicle_id, from_ms, to_ms, expires_at)
  values (encode(extensions.digest(v_token, 'sha256'), 'hex'), v_vehicle, p_from_ms, p_to_ms, v_expires);
  return jsonb_build_object('status', 'OK', 'token', v_token, 'expires_at', v_expires);
end;
$$;

revoke all on function public.create_share_link(bigint, bigint) from public, anon;
grant execute on function public.create_share_link(bigint, bigint) to authenticated;

-- 링크로 운행기록부를 읽는다 (로그인 없이). status: OK, EXPIRED (없거나 지난 링크)
create function public.shared_logbook(p_token text)
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
    'drivers', coalesce((select jsonb_object_agg(d.id, d.name) from public.drivers d where d.org_id = v_vehicle.org_id), '{}'::jsonb),
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

revoke all on function public.shared_logbook(text) from public;
grant execute on function public.shared_logbook(text) to anon, authenticated;

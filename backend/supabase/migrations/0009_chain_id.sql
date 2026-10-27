-- 차량 하나에 해시 체인이 여러 개일 수 있게 한다 (2026-10-27)
-- 실제 차는 VIN이 차량 ID라서, 동의를 철회하거나 앱을 다시 설치한 뒤 같은 차를 다시 연결하면 차 안 체인이 seq 1부터 새로 시작한다.
-- 전에는 서버가 (차량, seq)를 하나로만 받아서 그 뒤로 업로드가 계속 실패했다(409, 운행이 서버에 안 올라감).
-- 체인 ID = 그 체인 첫 기록(seq 1)의 해시. 해시 계산 규칙(docs/integrity.md)은 그대로이고, 이미 올라간 기록도 같은 규칙으로 채운다.

alter table public.trips add column chain_id text;
alter table public.trip_changes add column chain_id text;
alter table public.chain_heads add column chain_id text;

-- 지금까지는 차량마다 체인이 하나였으므로 그 차량의 seq 1 기록 해시가 체인 ID다
with starts as (
  select distinct on (vehicle_id) vehicle_id, hash from (
    select vehicle_id, hash, 0 as kind from public.trips where chain_seq = 1
    union all
    select vehicle_id, hash, 1 from public.trip_changes where chain_seq = 1
  ) s order by vehicle_id, kind
)
update public.trips t set chain_id = s.hash from starts s where s.vehicle_id = t.vehicle_id;

with starts as (
  select distinct on (vehicle_id) vehicle_id, hash from (
    select vehicle_id, hash, 0 as kind from public.trips where chain_seq = 1
    union all
    select vehicle_id, hash, 1 from public.trip_changes where chain_seq = 1
  ) s order by vehicle_id, kind
)
update public.trip_changes c set chain_id = s.hash from starts s where s.vehicle_id = c.vehicle_id;

with starts as (
  select distinct on (vehicle_id) vehicle_id, hash from (
    select vehicle_id, hash, 0 as kind from public.trips where chain_seq = 1
    union all
    select vehicle_id, hash, 1 from public.trip_changes where chain_seq = 1
  ) s order by vehicle_id, kind
)
update public.chain_heads h set chain_id = s.hash from starts s where s.vehicle_id = h.vehicle_id;

-- seq 1이 서버에 없는 차량(정상이라면 없다)은 따로 묶어 둔다
update public.trips set chain_id = 'unknown' where chain_id is null;
update public.trip_changes set chain_id = 'unknown' where chain_id is null;
update public.chain_heads set chain_id = 'unknown' where chain_id is null;

alter table public.trips alter column chain_id set not null;
alter table public.trip_changes alter column chain_id set not null;
alter table public.chain_heads alter column chain_id set not null;

alter table public.trips drop constraint trips_vehicle_id_chain_seq_key;
alter table public.trips add constraint trips_vehicle_chain_seq_key unique (vehicle_id, chain_id, chain_seq);
alter table public.trip_changes drop constraint trip_changes_vehicle_id_chain_seq_key;
alter table public.trip_changes add constraint trip_changes_vehicle_chain_seq_key unique (vehicle_id, chain_id, chain_seq);
alter table public.chain_heads drop constraint chain_heads_pkey;
alter table public.chain_heads add primary key (vehicle_id, chain_id);

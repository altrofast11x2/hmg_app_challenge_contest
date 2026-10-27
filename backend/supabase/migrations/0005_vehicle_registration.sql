-- 세무서 제출용 운행기록부 서식(국세청고시 제2025-8호 별지 「업무용승용차 운행기록부」)의 기본정보
-- ①차종 ②자동차등록번호를 차량에 적는다. 고시 제2조: 별도 서식으로 쓰더라도 차종과 자동차등록번호는 들어가야 한다.
-- 관리자 웹의 차량 탭에서 관리자가 적는다 (차량 이름·기본 운전자처럼 이 두 열만 더 고칠 수 있다).

alter table public.vehicles
  add column model text check (model is null or char_length(model) <= 40),            -- 차종 (예: 아이오닉 5)
  add column plate_number text check (plate_number is null or char_length(plate_number) <= 20);  -- 자동차등록번호 (예: 12가3456)

grant update (model, plate_number) on public.vehicles to authenticated;

// 앱의 LogbookBuilderTest·LogbookCsvTest와 같은 기대값으로 웹 계산을 확인한다
import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  BOM, buildLogbook, csvFileName, formDate, kstParts, logbookCsv, logbooksByVehicle, monthPeriod, periodFromRange, periodLabel, periodMonth,
  parseShareHash, periodRange, pickPeriod, statementCsv, statementFileName, taxFormNote, yearPeriod,
} from '../js/logbook.js';

// 2026-10-26(월) 09:00 한국 시간
const MON_9AM = Date.UTC(2026, 9, 26, 0, 0);

function trip(overrides = {}) {
  return {
    id: 't1',
    vehicle_id: 'v1',
    driver_id: 'd1',
    started_at_ms: MON_9AM,
    ended_at_ms: MON_9AM + 30 * 60 * 1000,
    start_odometer_km: 100.0,
    end_odometer_km: 112.3,
    visit_note: '고객사 A',
    purpose: 'BUSINESS',
    ...overrides,
  };
}

const names = { drivers: { d1: '기본 운전자' }, vehicles: { v1: '데모 차량' } };
const lines = (csv) => csv.slice(BOM.length).split('\r\n');

test('달의 범위는 한국 시간 1일 0시부터 다음 달 1일 0시 전까지다', () => {
  const [from, to] = periodRange(monthPeriod(2026, 10));
  assert.equal(from, Date.UTC(2026, 8, 30, 15, 0));
  assert.equal(to, Date.UTC(2026, 9, 31, 15, 0));
  assert.deepEqual(kstParts(from), { date: '2026-10-01', weekday: '목', time: '00:00' });
  assert.deepEqual(monthPeriod(2028, 2), { from: '2028-02-01', to: '2028-02-29' });
});

test('날짜로 고른 기간은 끝 날짜 하루를 포함하고, 시작과 끝이 바뀌면 바로잡는다', () => {
  const [from, to] = periodRange({ from: '2026-10-03', to: '2026-10-03' });
  assert.equal(to - from, 24 * 60 * 60 * 1000);
  assert.deepEqual(kstParts(from).date, '2026-10-03');
  assert.deepEqual(pickPeriod('2026-10-17', '2026-10-03'), { period: { from: '2026-10-03', to: '2026-10-17' } });
  assert.ok(pickPeriod('2026-10-03', '').error);
  assert.ok(pickPeriod('2025-01-01', '2026-10-01').error, '366일이 넘는 기간은 막는다');
  assert.deepEqual(pickPeriod('2025-10-01', '2026-09-30'), { period: { from: '2025-10-01', to: '2026-09-30' } });
});

test('한 달 전체를 고르면 달로 보이고, 아니면 날짜 범위로 보인다', () => {
  assert.deepEqual(periodMonth({ from: '2026-10-01', to: '2026-10-31' }), { year: 2026, month: 10 });
  assert.equal(periodMonth({ from: '2026-10-01', to: '2026-10-30' }), null);
  assert.equal(periodLabel({ from: '2026-10-01', to: '2026-10-31' }), '2026년 10월');
  assert.equal(periodLabel({ from: '2026-10-03', to: '2026-10-17' }), '2026.10.03 – 2026.10.17');
  assert.equal(periodLabel({ from: '2026-10-03', to: '2026-10-03' }), '2026.10.03');
});

test('업무용 사용거리는 출퇴근과 일반업무만 더하고 미분류·비업무는 넣지 않는다', () => {
  const logbook = buildLogbook([
    trip({ id: 'a', purpose: 'COMMUTE', start_odometer_km: 0, end_odometer_km: 10 }),
    trip({ id: 'b', purpose: 'BUSINESS', start_odometer_km: 10, end_odometer_km: 40 }),
    trip({ id: 'c', purpose: 'PERSONAL', start_odometer_km: 40, end_odometer_km: 50, visit_note: null }),
    trip({ id: 'd', purpose: 'UNCLASSIFIED', start_odometer_km: 50, end_odometer_km: 60 }),
  ], names);
  const { summary } = logbook;
  assert.equal(summary.totalKm, 60);
  assert.equal(summary.commuteKm, 10);
  assert.equal(summary.businessKm, 30);
  assert.equal(summary.workKm, 40);
  assert.equal(summary.workRatioPercent.toFixed(1), '66.7');
  assert.equal(summary.unclassifiedCount, 1);
  assert.equal(summary.tripCount, 4);
});

test('계기판 값이 없으면 주행거리는 0이고, 운행이 없으면 업무사용비율은 비운다', () => {
  const logbook = buildLogbook([trip({ start_odometer_km: null })], names);
  assert.equal(logbook.rows[0].distanceKm, 0);
  assert.equal(buildLogbook([], names).summary.workRatioPercent, null);
});

test('운행은 시작 시각 순서로 줄을 세운다', () => {
  const logbook = buildLogbook([trip({ id: 'late', started_at_ms: MON_9AM + 1000 }), trip({ id: 'early' })], names);
  assert.deepEqual(logbook.rows.map((row) => row.tripId), ['early', 'late']);
});

test('CSV는 UTF-8 BOM으로 시작하고 CRLF로 줄을 나누며 앱과 같은 서식으로 쓴다', () => {
  const csv = logbookCsv(buildLogbook([trip()], names));
  assert.ok(csv.startsWith(BOM));
  assert.ok(lines(csv)[0].startsWith('사용일자,사용자,주행 전 계기판 거리(km)'));
  assert.equal(lines(csv)[1], '2026-10-26(월),기본 운전자,100.0,112.3,12.3,0.0,12.3,고객사 A,일반업무');
  assert.ok(csv.includes('총 주행거리(km),12.3\r\n'));
  assert.ok(csv.includes('업무용 사용거리(km),12.3\r\n'));
  assert.ok(csv.includes('업무사용비율(%),100.0\r\n'));
  assert.ok(csv.endsWith('미분류 운행(건),0\r\n'));
});

test('쉼표·따옴표·줄바꿈이 든 비고는 따옴표로 감싸고, 수식으로 시작하는 글자는 막는다', () => {
  assert.ok(logbookCsv(buildLogbook([trip({ visit_note: '서울 "본사", 3층\n회의' })], names))
    .includes('"서울 ""본사"", 3층\n회의"'));
  assert.ok(logbookCsv(buildLogbook([trip({ visit_note: '=HYPERLINK("x")' })], names)).includes(`"'=HYPERLINK(""x"")"`));
  assert.ok(logbookCsv(buildLogbook([trip({ visit_note: '-12.5' })], names)).includes(',-12.5,'));
});

test('여러 차량을 함께 내보낼 때만 차량 열을 넣는다', () => {
  const csv = logbookCsv(buildLogbook([trip()], names), { includeVehicle: true });
  assert.ok(lines(csv)[0].startsWith('사용일자,차량,사용자'));
  assert.ok(lines(csv)[1].startsWith('2026-10-26(월),데모 차량,기본 운전자'));
});

test('CSV 파일 이름에 쓸 수 없는 글자는 바꾼다', () => {
  assert.equal(csvFileName(monthPeriod(2026, 10)), 'DriveLog_2026-10.csv');
  assert.equal(csvFileName(monthPeriod(2026, 3), '영업 1호/차'), 'DriveLog_2026-03_영업_1호_차.csv');
  assert.equal(csvFileName({ from: '2026-10-03', to: '2026-10-17' }), 'DriveLog_2026-10-03_2026-10-17.csv');
});

test('세무서 제출용: 차량마다 기록부를 만들고, 비고에 비업무·미분류를 붙이며, 명세서용 값을 CSV로 쓴다', () => {
  const vehicles = [
    { id: 'v1', name: '데모 차량', model: '아이오닉 5', plateNumber: '12가3456' },
    { id: 'v2', name: '영업 2호차', model: null, plateNumber: null },
  ];
  const trips = [
    trip({ id: 'a', vehicle_id: 'v1', purpose: 'BUSINESS', start_odometer_km: 100, end_odometer_km: 130, visit_note: '고객사 A' }),
    trip({ id: 'b', vehicle_id: 'v1', purpose: 'PERSONAL', start_odometer_km: 130, end_odometer_km: 140, visit_note: null }),
  ];
  const byVehicle = logbooksByVehicle(vehicles, trips, names);
  assert.deepEqual(byVehicle.map(({ logbook }) => logbook.rows.length), [2, 0]);
  assert.equal(taxFormNote(byVehicle[0].logbook.rows[0]), '고객사 A');
  assert.equal(taxFormNote(byVehicle[0].logbook.rows[1]), '비업무');
  assert.equal(formDate('2026-01-01'), '2026. 1. 1.');
  assert.deepEqual(yearPeriod(2026), { from: '2026-01-01', to: '2026-12-31' });

  const csv = lines(statementCsv(byVehicle, yearPeriod(2026)));
  assert.equal(csv[0], '사업연도 2026-01-01 ~ 2026-12-31');
  assert.equal(csv[2], '데모 차량,아이오닉 5,12가3456,2,40.0,30.0,75.0');
  assert.equal(csv[3], '영업 2호차,,,0,0.0,0.0,');
  assert.equal(statementFileName(yearPeriod(2026)), 'DriveLog_업무사용비율_2026-01-01_2026-12-31.csv');
});

test('휴대폰으로 받기: 주소의 # 뒤에서 토큰과 차에서 고른 기간을 꺼내고, 서버 범위를 한국 날짜 기간으로 바꾼다', () => {
  const token = 'AbC-_123456789012345678901234567';
  assert.deepEqual(parseShareHash(`#share=${token}&from=2026-10-01&to=2026-10-31`), { token, period: { from: '2026-10-01', to: '2026-10-31' } });
  assert.deepEqual(parseShareHash(`#share=${token}`), { token, period: null });
  assert.deepEqual(parseShareHash(`#share=${token}&from=2024-01-01&to=2026-10-31`), { token, period: null }, '366일이 넘는 기간은 버린다');
  assert.equal(parseShareHash(''), null);
  assert.equal(parseShareHash('#share=짧음'), null);
  assert.equal(parseShareHash(`#other=${token}`), null);
  assert.deepEqual(periodFromRange(...periodRange(monthPeriod(2026, 10))), { from: '2026-10-01', to: '2026-10-31' });
  assert.deepEqual(periodFromRange(...periodRange({ from: '2026-10-26', to: '2026-10-26' })), { from: '2026-10-26', to: '2026-10-26' });
});

test('운행 중에 계기판이 줄었으면(교체·초기화) 거리를 0으로 보고 합계에서 빼지 않는다 (앱과 같다)', () => {
  const logbook = buildLogbook([
    trip({ id: 'a', start_odometer_km: 100, end_odometer_km: 120 }),
    trip({ id: 'b', started_at_ms: MON_9AM + 1000, start_odometer_km: 208, end_odometer_km: 3 }),
  ], names);
  assert.deepEqual(logbook.rows.map((row) => row.distanceKm), [20, 0]);
  assert.equal(logbook.summary.totalKm, 20);
});

// ─── 공용차 운전자·업무 일지 (0010) ───

test('사용자 칸은 운행 당시 이름·부서를 먼저 쓰고, 비고는 방문처 · 업무 내용', async () => {
  const { buildLogbook } = await import('../js/logbook.js');
  const base = { vehicle_id: 'v1', start_odometer_km: 100, end_odometer_km: 108, purpose: 'BUSINESS' };
  const logbook = buildLogbook([
    { ...base, id: 't1', started_at_ms: Date.UTC(2026, 10, 4, 1), driver_id: 'd1', driver_name: '이서연', driver_department: '기술지원팀',
      visit_note: '수정구 시흥동', work_note: '거래처 납품 미팅' },
    { ...base, id: 't2', started_at_ms: Date.UTC(2026, 10, 4, 2), driver_id: 'd2', visit_note: '판교', work_note: null },
  ], { drivers: { d1: '이서연(옛 이름)', d2: '김민준' }, departments: { d2: '영업1팀' } });
  assert.equal(logbook.rows[0].driver, '이서연');
  assert.equal(logbook.rows[0].department, '기술지원팀');
  assert.equal(logbook.rows[0].note, '수정구 시흥동 · 거래처 납품 미팅');
  assert.equal(logbook.rows[1].driver, '김민준');
  assert.equal(logbook.rows[1].department, '영업1팀');
  assert.equal(logbook.rows[1].note, '판교');
});

test('업무 일지는 일반업무 운행만 날짜별로 모으고 최근 날짜가 위', async () => {
  const { buildLogbook, workJournal, workJournalCsv } = await import('../js/logbook.js');
  const trip = (id, day, purpose, km, note) => ({
    id, vehicle_id: 'v1', started_at_ms: Date.UTC(2026, 10, day, 1), start_odometer_km: 0, end_odometer_km: km, purpose,
    driver_name: '김민준', driver_department: '영업1팀', visit_note: '판교', work_note: note,
  });
  const days = workJournal(buildLogbook([
    trip('a', 3, 'BUSINESS', 10, '견적 상담'),
    trip('b', 3, 'PERSONAL', 5, null),
    trip('c', 4, 'BUSINESS', 7.5, '납품'),
    trip('d', 4, 'COMMUTE', 12, null),
  ], { vehicles: { v1: '1호차' } }));
  assert.deepEqual(days.map((day) => day.date), ['2026-11-04', '2026-11-03']);
  assert.equal(days[0].entries.length, 1);
  assert.equal(days[1].km, 10);
  const csv = workJournalCsv(days);
  assert.ok(csv.startsWith('\uFEFF날짜,시간,차량,운전자,부서,방문처,업무 내용,주행거리(km)'));
  assert.ok(csv.includes('1호차,김민준,영업1팀,판교,납품,7.5'));
});

// 월별 운행기록부와 CSV. 앱의 LogbookBuilder·LogbookCsv(Kotlin)와 같은 규칙으로 계산해서 앱과 웹의 결과가 같다.
// 화면(DOM)을 쓰지 않는 순수 함수라 node --test로 시험한다.

export const PURPOSE_LABELS = {
  UNCLASSIFIED: '미분류',
  COMMUTE: '출퇴근',
  BUSINESS: '일반업무',
  PERSONAL: '비업무',
};

// 한국 시간(UTC+9, 서머타임 없음) 기준으로 날짜를 계산한다
const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

/** 그 달에 시작한 운행을 고르는 범위 [from, to) (epoch 밀리초) */
export function monthRange(year, month) {
  return [Date.UTC(year, month - 1, 1) - KST_OFFSET_MS, Date.UTC(year, month, 1) - KST_OFFSET_MS];
}

const pad = (n) => String(n).padStart(2, '0');

/** epoch 밀리초 → 한국 시간 { date: '2026-10-26', weekday: '월', time: '14:05' } */
export function kstParts(ms) {
  const d = new Date(ms + KST_OFFSET_MS);
  return {
    date: `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`,
    weekday: WEEKDAYS[d.getUTCDay()],
    time: `${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}`,
  };
}

/**
 * 서버의 trips 행을 월별 운행기록부로 바꾼다.
 * names.drivers / names.vehicles: id → 이름 (없으면 '-')
 */
export function buildLogbook(trips, names = {}) {
  const drivers = names.drivers ?? {};
  const vehicles = names.vehicles ?? {};
  const rows = [...trips]
    .sort((a, b) => a.started_at_ms - b.started_at_ms)
    .map((trip) => {
      const start = trip.start_odometer_km;
      const end = trip.end_odometer_km;
      const distanceKm = start != null && end != null ? end - start : 0;
      const started = kstParts(trip.started_at_ms);
      return {
        tripId: trip.id,
        date: started.date,
        weekday: started.weekday,
        startTime: started.time,
        endTime: trip.ended_at_ms != null ? kstParts(trip.ended_at_ms).time : null,
        vehicle: vehicles[trip.vehicle_id] ?? '-',
        driver: drivers[trip.driver_id] ?? '-',
        startOdometerKm: start,
        endOdometerKm: end,
        distanceKm,
        commuteKm: trip.purpose === 'COMMUTE' ? distanceKm : 0,
        businessKm: trip.purpose === 'BUSINESS' ? distanceKm : 0,
        note: trip.visit_note ?? null,
        purpose: trip.purpose,
      };
    });

  const sum = (key) => rows.reduce((total, row) => total + row[key], 0);
  const totalKm = sum('distanceKm');
  const commuteKm = sum('commuteKm');
  const businessKm = sum('businessKm');
  const workKm = commuteKm + businessKm;
  return {
    rows,
    summary: {
      totalKm,
      commuteKm,
      businessKm,
      // 업무용 사용거리 = 출퇴근용 + 일반 업무용
      workKm,
      // 업무사용비율(%) = 업무용 사용거리 ÷ 총 주행거리. 미분류 운행은 업무용에 넣지 않는다
      workRatioPercent: totalKm > 0 ? (workKm / totalKm) * 100 : null,
      tripCount: rows.length,
      unclassifiedCount: rows.filter((row) => row.purpose === 'UNCLASSIFIED').length,
    },
  };
}

export const BOM = '﻿';

const HEADER = [
  '사용일자', '사용자', '주행 전 계기판 거리(km)', '주행 후 계기판 거리(km)', '주행거리(km)',
  '업무용 사용거리 출퇴근용(km)', '업무용 사용거리 일반업무용(km)', '비고(방문처)', '구분',
];

/** 거리는 소수 첫째 자리까지 (값이 없으면 빈칸) */
export const km = (value) => (value == null ? '' : value.toFixed(1));

/**
 * 월별 운행기록부 CSV. Excel에서 한글이 깨지지 않도록 UTF-8 BOM을 붙이고 줄바꿈은 CRLF로 쓴다.
 * 열은 국세청 업무용승용차 운행기록부 서식 순서를 따른다. 여러 차량을 함께 내보낼 때만 '차량' 열을 넣는다.
 */
export function logbookCsv(logbook, { includeVehicle = false } = {}) {
  const header = includeVehicle ? [HEADER[0], '차량', ...HEADER.slice(1)] : HEADER;
  const rows = logbook.rows.map((row) => {
    const line = [
      `${row.date}(${row.weekday})`,
      row.driver,
      km(row.startOdometerKm),
      km(row.endOdometerKm),
      km(row.distanceKm),
      km(row.commuteKm),
      km(row.businessKm),
      row.note ?? '',
      PURPOSE_LABELS[row.purpose] ?? row.purpose,
    ];
    if (includeVehicle) line.splice(1, 0, row.vehicle);
    return line;
  });
  const { summary } = logbook;
  const footer = [
    [],
    ['총 주행거리(km)', km(summary.totalKm)],
    ['업무용 사용거리(km)', km(summary.workKm)],
    ['업무사용비율(%)', km(summary.workRatioPercent)],
    ['미분류 운행(건)', String(summary.unclassifiedCount)],
  ];
  return BOM + [header, ...rows, ...footer].map((line) => line.map(escapeCsv).join(',')).join('\r\n') + '\r\n';
}

/** 파일 이름에 쓸 수 없는 글자는 _로 바꾼다 */
export function csvFileName(year, month, vehicleName) {
  const base = `DriveLog_${year}-${pad(month)}`;
  return vehicleName ? `${base}_${vehicleName.replace(/[\\/:*?"<>|\s]+/g, '_')}.csv` : `${base}.csv`;
}

const FORMULA_PREFIX = /^[=+\-@]/;

/** 쉼표·따옴표·줄바꿈은 따옴표로 감싸고, =·+·-·@로 시작하는 글자는 Excel 수식으로 실행되지 않게 막는다 */
function escapeCsv(value) {
  const isNumber = value.trim() !== '' && !Number.isNaN(Number(value));
  const safe = FORMULA_PREFIX.test(value) && !isNumber ? `'${value}` : value;
  return /[",\r\n]/.test(safe) ? `"${safe.replaceAll('"', '""')}"` : safe;
}

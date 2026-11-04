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

const DAY_MS = 24 * 60 * 60 * 1000;

/** 한 번에 고를 수 있는 기간 (서버 응답이 너무 커지지 않게, 운행기록부 한 사업연도 정도) */
export const MAX_PERIOD_DAYS = 366;

const pad = (n) => String(n).padStart(2, '0');

/**
 * 기간 { from: '2026-10-03', to: '2026-10-17' } (한국 날짜, 끝 날짜 포함)에 시작한 운행을 고르는 범위 [from, to) (epoch 밀리초)
 */
export function periodRange({ from, to }) {
  const [y1, m1, d1] = from.split('-').map(Number);
  const [y2, m2, d2] = to.split('-').map(Number);
  return [Date.UTC(y1, m1 - 1, d1) - KST_OFFSET_MS, Date.UTC(y2, m2 - 1, d2 + 1) - KST_OFFSET_MS];
}

/** 그 달 전체 기간 { from: '2026-10-01', to: '2026-10-31' } */
export function monthPeriod(year, month) {
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  return { from: `${year}-${pad(month)}-01`, to: `${year}-${pad(month)}-${pad(lastDay)}` };
}

/** 기간이 한 달 전체면 { year, month }, 아니면 null */
export function periodMonth(period) {
  const [year, month] = period.from.split('-').map(Number);
  const whole = monthPeriod(year, month);
  return whole.from === period.from && whole.to === period.to ? { year, month } : null;
}

/**
 * 날짜 칸 두 개에서 고른 값을 기간으로 바꾼다. 시작이 끝보다 늦으면 서로 바꾼다.
 * 반환: { period } 또는 { error: '화면에 보여 줄 문장' }
 */
export function pickPeriod(from, to) {
  if (!from || !to) return { error: '시작 날짜와 끝 날짜를 모두 고르세요.' };
  const period = from <= to ? { from, to } : { from: to, to: from };
  const [start, end] = periodRange(period);
  if ((end - start) / DAY_MS > MAX_PERIOD_DAYS) return { error: `기간은 ${MAX_PERIOD_DAYS}일까지 고를 수 있어요.` };
  return { period };
}

/**
 * 휴대폰으로 받기 주소의 # 뒤를 읽는다: '#share=토큰&from=2026-10-01&to=2026-10-31' → { token, period }.
 * 기간은 차에서 고른 날짜(차의 시간대)라서 화면 제목에 그대로 쓴다. 기간이 없거나 이상하면 period는 null. 토큰이 없으면 null
 */
export function parseShareHash(hash) {
  const match = /^#share=([A-Za-z0-9_-]{16,128})(?:&from=(\d{4}-\d{2}-\d{2})&to=(\d{4}-\d{2}-\d{2}))?$/.exec(hash ?? '');
  if (!match) return null;
  const picked = match[2] ? pickPeriod(match[2], match[3]) : {};
  return { token: match[1], period: picked.period ?? null };
}

/** 서버 범위 [fromMs, toMs) → 기간 { from, to } (한국 날짜, 끝 날짜 포함) */
export function periodFromRange(fromMs, toMs) {
  return { from: kstParts(fromMs).date, to: kstParts(toMs - 1).date };
}

/** 그해 1월 1일부터 12월 31일까지 (사업연도 기본값) */
export function yearPeriod(year) {
  return { from: `${year}-01-01`, to: `${year}-12-31` };
}

/** 화면 제목: 한 달 전체면 '2026년 10월', 아니면 '2026.10.03 – 2026.10.17' */
export function periodLabel(period) {
  const month = periodMonth(period);
  if (month) return `${month.year}년 ${month.month}월`;
  const dotted = (date) => date.replaceAll('-', '.');
  return period.from === period.to ? dotted(period.from) : `${dotted(period.from)} – ${dotted(period.to)}`;
}

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
 * names.drivers / names.vehicles: id → 이름 (없으면 '-'). names.departments: 운전자 id → 부서
 * 사용자 칸은 차가 운행 당시에 적은 이름·부서(driver_name, driver_department, 0010)를 먼저 쓴다 (나중에 명단에서 이름이 바뀌어도 기록은 그대로)
 */
export function buildLogbook(trips, names = {}) {
  const drivers = names.drivers ?? {};
  const departments = names.departments ?? {};
  const vehicles = names.vehicles ?? {};
  const rows = [...trips]
    .sort((a, b) => a.started_at_ms - b.started_at_ms)
    .map((trip) => {
      const start = trip.start_odometer_km;
      const end = trip.end_odometer_km;
      // 계기판 값이 없거나 운행 중에 계기판이 줄었으면(교체·초기화) 거리를 알 수 없어 0으로 본다 (앱과 같다)
      const distanceKm = start != null && end != null && end >= start ? end - start : 0;
      const started = kstParts(trip.started_at_ms);
      return {
        tripId: trip.id,
        date: started.date,
        weekday: started.weekday,
        startTime: started.time,
        endTime: trip.ended_at_ms != null ? kstParts(trip.ended_at_ms).time : null,
        vehicleId: trip.vehicle_id,
        vehicle: vehicles[trip.vehicle_id] ?? '-',
        driver: trip.driver_name ?? drivers[trip.driver_id] ?? '-',
        department: trip.driver_department ?? departments[trip.driver_id] ?? '',
        startOdometerKm: start,
        endOdometerKm: end,
        distanceKm,
        commuteKm: trip.purpose === 'COMMUTE' ? distanceKm : 0,
        businessKm: trip.purpose === 'BUSINESS' ? distanceKm : 0,
        // 비고 = 방문처 · 업무 내용 (음성 업무 일지)
        note: [trip.visit_note, trip.work_note].filter(Boolean).join(' · ') || null,
        visitNote: trip.visit_note ?? null,
        workNote: trip.work_note ?? null,
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

export const BOM = '\uFEFF';

const HEADER = [
  '사용일자', '사용자', '주행 전 계기판 거리(km)', '주행 후 계기판 거리(km)', '주행거리(km)',
  '업무용 사용거리 출퇴근용(km)', '업무용 사용거리 일반업무용(km)', '비고(방문처·업무 내용)', '구분',
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

/** 한 달 전체면 DriveLog_2026-10, 아니면 DriveLog_2026-10-03_2026-10-17. 파일 이름에 쓸 수 없는 글자는 _로 바꾼다 */
export function csvFileName(period, vehicleName) {
  const month = periodMonth(period);
  const base = month ? `DriveLog_${month.year}-${pad(month.month)}` : `DriveLog_${period.from}_${period.to}`;
  return vehicleName ? `${base}_${vehicleName.replace(/[\\/:*?"<>|\s]+/g, '_')}.csv` : `${base}.csv`;
}

// ─── 업무 일지 ─────────────────────────────────────────────────────────
// 일반업무 운행만 날짜별로 모은다: 언제·누가·어느 차로·어디서·무슨 일을 했는지 (외근 일지)

/** 운행기록부에서 일반업무 운행을 날짜별로 묶는다. 최근 날짜가 위 */
export function workJournal(logbook) {
  const days = new Map();
  for (const row of logbook.rows) {
    if (row.purpose !== 'BUSINESS') continue;
    if (!days.has(row.date)) days.set(row.date, { date: row.date, weekday: row.weekday, entries: [], km: 0 });
    const day = days.get(row.date);
    day.entries.push(row);
    day.km += row.distanceKm;
  }
  return [...days.values()].sort((a, b) => b.date.localeCompare(a.date));
}

const JOURNAL_HEADER = ['날짜', '시간', '차량', '운전자', '부서', '방문처', '업무 내용', '주행거리(km)'];

export function workJournalCsv(days) {
  const rows = days.flatMap((day) => [...day.entries].sort((a, b) => a.startTime.localeCompare(b.startTime)).map((row) => [
    `${row.date}(${row.weekday})`,
    row.endTime ? `${row.startTime}-${row.endTime}` : row.startTime,
    row.vehicle,
    row.driver,
    row.department,
    row.visitNote ?? '',
    row.workNote ?? '',
    km(row.distanceKm),
  ]));
  return BOM + [JOURNAL_HEADER, ...rows].map((line) => line.map(escapeCsv).join(',')).join('\r\n') + '\r\n';
}

export const workJournalFileName = (period) => {
  const month = periodMonth(period);
  return month ? `DriveLog_업무일지_${month.year}-${pad(month.month)}.csv` : `DriveLog_업무일지_${period.from}_${period.to}.csv`;
};

// ─── 세무서 제출용 ─────────────────────────────────────────────────────
// 운행기록부는 작성해 보관하다가 세무서가 요구하면 제출한다. 서식은 국세청고시 제2025-8호 별지
// 「업무용승용차 운행기록부」: 사업연도·법인명·사업자등록번호, ①차종 ②자동차등록번호, ③~⑩ 운행 줄, ⑪~⑬ 합계

/** 서식의 ⑩비고: 방문처에 비업무·미분류 표시를 붙인다 (서식에 용도 칸이 없어서) */
export function taxFormNote(row) {
  const kind = { PERSONAL: '비업무', UNCLASSIFIED: '미분류' }[row.purpose];
  return [row.note, kind].filter(Boolean).join(' · ');
}

/** 서식의 사업연도 날짜: '2026-01-01' → '2026. 1. 1.' */
export function formDate(date) {
  const [year, month, day] = date.split('-').map(Number);
  return `${year}. ${month}. ${day}.`;
}

/**
 * 차량마다 운행기록부를 만든다 (서식은 차량별로 한 장). 운행이 없는 차량도 빈 기록부로 넣는다.
 * vehicles: [{ id, name, model, plateNumber }]
 */
export function logbooksByVehicle(vehicles, trips, names = {}) {
  return vehicles.map((vehicle) => ({
    vehicle,
    logbook: buildLogbook(trips.filter((trip) => trip.vehicle_id === vehicle.id), names),
  }));
}

const STATEMENT_HEADER = [
  '차량', '차종', '자동차등록번호', '운행(건)', '사업연도 총주행 거리(km)', '사업연도 업무용 사용거리(km)', '업무사용비율(%)',
];

/** 업무용승용차 관련비용 명세서를 쓸 때 옮겨 적을 차량별 운행기록 값 (서식 ⑪~⑬) */
export function statementCsv(byVehicle, period) {
  const rows = byVehicle.map(({ vehicle, logbook: { summary } }) => [
    vehicle.name, vehicle.model ?? '', vehicle.plateNumber ?? '', String(summary.tripCount),
    km(summary.totalKm), km(summary.workKm), km(summary.workRatioPercent),
  ]);
  const title = [`사업연도 ${period.from} ~ ${period.to}`];
  return BOM + [title, STATEMENT_HEADER, ...rows].map((line) => line.map(escapeCsv).join(',')).join('\r\n') + '\r\n';
}

export const statementFileName = (period) => `DriveLog_업무사용비율_${period.from}_${period.to}.csv`;

const FORMULA_PREFIX = /^[=+\-@]/;

/** 쉼표·따옴표·줄바꿈은 따옴표로 감싸고, =·+·-·@로 시작하는 글자는 Excel 수식으로 실행되지 않게 막는다 */
function escapeCsv(value) {
  const isNumber = value.trim() !== '' && !Number.isNaN(Number(value));
  const safe = FORMULA_PREFIX.test(value) && !isNumber ? `'${value}` : value;
  return /[",\r\n]/.test(safe) ? `"${safe.replaceAll('"', '""')}"` : safe;
}

// 관리자 웹 화면: 로그인 → 현황 / 운행기록부 / 업무 일지 / 세무 제출 / 운전자 / 직원 / 차량(연결 코드 발급 포함)
// 서버에서 온 글자는 항상 textContent로 넣는다 (innerHTML을 쓰지 않아 스크립트 삽입을 막는다).
import * as api from './api.js';
import {
  buildLogbook, csvFileName, formDate, km, kstParts, logbookCsv, logbooksByVehicle, monthPeriod, periodFromRange, periodLabel, periodMonth,
  parseShareHash, periodRange, pickPeriod, PURPOSE_LABELS, statementCsv, statementFileName, taxFormNote, workJournal, workJournalCsv,
  workJournalFileName, yearPeriod,
} from './logbook.js';
import { normalizeJoinCode, SIGN_UP_MESSAGES, signUpProblem } from './signup.js';

const root = document.getElementById('app');

// 연결 코드 발급은 차량 탭 안에 둔다 (앱 연결 화면 안내 "관리자 웹 › 차량 › 연결 코드 발급", design/specs/A02-connect.md)
const TABS = [
  { id: 'overview', label: '현황' },
  { id: 'logbook', label: '운행기록부' },
  { id: 'journal', label: '업무 일지' },
  { id: 'tax', label: '세무 제출' },
  { id: 'drivers', label: '운전자', adminOnly: true },
  { id: 'members', label: '직원', adminOnly: true },
  { id: 'vehicles', label: '차량', adminOnly: true },
];

// 직원(가입 승인을 받은 운전자)은 배정받은 차량의 운행기록부와 세무 제출만 본다
const tabsFor = (role) => TABS.filter((tab) => role === 'admin' || !tab.adminOnly);

const state = {
  orgs: [],
  org: null,
  tab: 'overview',
  role: 'admin', // 'admin' = 기업 관리자, 'member' = 직원
  period: monthPeriod(currentMonth().year, currentMonth().month), // 운행기록부 기간 (한국 날짜, 끝 날짜 포함)
  taxPeriod: yearPeriod(currentMonth().year), // 세무 제출 탭의 사업연도 (기본값: 올해 1월 1일 ~ 12월 31일)
  vehicleId: '', // 빈칸 = 전체 차량
  vehicles: [],
  drivers: [],
};

// 화면을 바꾸면 이전 화면의 타이머와 늦게 도착한 응답을 버린다
let renderToken = 0;
let timer = null;

// ─── 작은 도우미 ───────────────────────────────────────────────────────

function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value == null || value === false) continue;
    if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
    else if (key === 'class') node.className = value;
    else node.setAttribute(key, value === true ? '' : value);
  }
  for (const child of children.flat()) {
    if (child != null && child !== false) node.append(child);
  }
  return node;
}

/** 앱과 같은 선 아이콘 (24 격자, 2.2 획). 정해진 path만 그린다 */
function lineIcon(...paths) {
  const ns = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(ns, 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('aria-hidden', 'true');
  svg.setAttribute('fill', 'none');
  svg.setAttribute('stroke', 'currentColor');
  svg.setAttribute('stroke-width', '2.2');
  svg.setAttribute('stroke-linecap', 'round');
  svg.setAttribute('stroke-linejoin', 'round');
  for (const d of paths) {
    const path = document.createElementNS(ns, 'path');
    path.setAttribute('d', d);
    svg.append(path);
  }
  return svg;
}

/** 앱 표시: 남색 사각 안의 계기판 아이콘 (앱의 동의 화면과 같다) */
const appMark = () => el('span', { class: 'app-mark' }, lineIcon('M4 16a8 8 0 1 1 16 0', 'M12 16l4-5', 'M7.5 19.5h9'));

function currentMonth() {
  const [year, month] = kstParts(Date.now()).date.split('-').map(Number);
  return { year, month };
}

function formatDateTime(iso) {
  if (!iso) return '-';
  const parts = kstParts(Date.parse(iso));
  return `${parts.date} ${parts.time}`;
}

const vehicleName = (vehicle) => vehicle.display_name || vehicle.vin || '차량';
const driverName = (id) => state.drivers.find((driver) => driver.id === id)?.name ?? '-';

function names() {
  return {
    drivers: Object.fromEntries(state.drivers.map((driver) => [driver.id, driver.name])),
    departments: Object.fromEntries(state.drivers.map((driver) => [driver.id, driver.department ?? ''])),
    vehicles: Object.fromEntries(state.vehicles.map((vehicle) => [vehicle.id, vehicleName(vehicle)])),
  };
}

function message(text, kind = 'info') {
  return el('p', { class: `message message-${kind}`, role: kind === 'error' ? 'alert' : null }, text);
}

/** 로그인이 풀렸으면 로그인 화면으로, 아니면 오류 문구를 돌려준다 */
function failure(error) {
  if (error.status === 401) {
    renderLogin(error.message);
    return null;
  }
  return message(error.message || '알 수 없는 오류가 났어요.', 'error');
}

function download(text, fileName) {
  const url = URL.createObjectURL(new Blob([text], { type: 'text/csv;charset=utf-8' }));
  const link = el('a', { href: url, download: fileName });
  document.body.append(link);
  link.click();
  link.remove();
  // 바로 지우면 일부 휴대폰 브라우저(iOS Safari 등)가 저장을 끝내기 전에 파일을 잃는다
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

function setScreen(...children) {
  renderToken += 1;
  clearInterval(timer);
  root.replaceChildren(...children);
  return renderToken;
}

// ─── 시작과 로그인 ─────────────────────────────────────────────────────

async function boot() {
  const config = await import('./config.js').catch(() => null);
  if (!config?.SUPABASE_URL || !config?.SUPABASE_KEY) {
    setScreen(el('main', { class: 'center' },
      el('h1', {}, '설정이 필요해요'),
      el('p', {}, 'web/js/config.js가 없어요. 저장소 루트에서 node web/scripts/write-config.mjs를 실행하세요.'),
    ));
    return;
  }
  api.configure({ url: config.SUPABASE_URL, key: config.SUPABASE_KEY });
  // 차 화면의 QR 코드로 연 휴대폰은 로그인 없이 그 운행기록부만 보여 준다
  const share = parseShareHash(location.hash);
  if (share) {
    await renderShare(share);
    return;
  }
  if (api.currentUser()) await enter();
  else renderLogin();
}

/** 로그아웃·세션 만료로 로그인 화면에 오면 이전 계정의 화면 상태를 지운다 (다른 계정이 이어서 로그인해도 필터가 남지 않게) */
function resetState() {
  Object.assign(state, {
    orgs: [], org: null, tab: 'overview', role: 'admin', vehicleId: '', vehicles: [], drivers: [],
    period: monthPeriod(currentMonth().year, currentMonth().month), taxPeriod: yearPeriod(currentMonth().year),
  });
}

function renderLogin(notice) {
  resetState();
  const status = el('div', { class: 'form-status' }, notice ? message(notice, 'error') : null);
  const button = el('button', { type: 'submit', class: 'button-primary' }, '로그인');
  const form = el('form', { class: 'card login', onsubmit: onSubmit },
    el('div', { class: 'brand-mark' }, appMark(), el('h1', {}, '차곡')),
    el('label', {}, '아이디 또는 이메일', el('input', {
      name: 'id', type: 'text', autocomplete: 'username', autocapitalize: 'off', spellcheck: 'false', required: true,
    })),
    el('label', {}, '비밀번호',
      el('input', { name: 'password', type: 'password', autocomplete: 'current-password', required: true })),
    status,
    button,
    el('p', { class: 'login-signup muted' }, '회사 차를 쓰는 직원인가요? ',
      el('button', { type: 'button', class: 'button-text', onclick: () => renderSignUp() }, '직원 회원가입')),
  );
  setScreen(el('main', { class: 'center' }, form));

  async function onSubmit(event) {
    event.preventDefault();
    const data = new FormData(form);
    button.disabled = true;
    button.textContent = '로그인 중…';
    try {
      await api.signIn(String(data.get('id')), String(data.get('password')));
      await enter();
    } catch (error) {
      const text = error.code === 'invalid_credentials' ? '아이디 또는 비밀번호가 맞지 않아요.' : error.message;
      status.replaceChildren(message(text, 'error'));
      button.disabled = false;
      button.textContent = '로그인';
    }
  }
}

async function enter() {
  setScreen(el('main', { class: 'center' }, message('불러오는 중…')));
  try {
    // 개발자(서비스 운영) 계정은 기업 등록 화면으로
    if (await api.isDeveloper()) {
      renderDeveloper();
      return;
    }
    state.orgs = await api.adminOrgs();
    state.role = 'admin';
    if (state.orgs.length === 0) {
      // 관리자가 아니면 승인된 직원인지, 가입 신청 중인지 본다
      state.orgs = await api.memberOrgs();
      state.role = 'member';
    }
    if (state.orgs.length === 0) {
      renderNoAccess(await api.myMemberRequest());
      return;
    }
    if (!tabsFor(state.role).some((tab) => tab.id === state.tab)) state.tab = 'overview';
    state.org = state.orgs.find((org) => org.id === state.org?.id) ?? state.orgs[0];
    await loadOrgData();
    // 고른 차량이 목록에 없으면(연결 해제·배정 해제) 전체 차량으로 돌린다. 안 그러면 "전체 차량"으로 보이면서 빈 표가 나온다
    if (!state.vehicles.some((vehicle) => vehicle.id === state.vehicleId)) state.vehicleId = '';
    renderMain();
  } catch (error) {
    const shown = failure(error);
    if (shown) setScreen(el('main', { class: 'center' }, shown, el('button', { class: 'button-secondary', onclick: logout }, '로그아웃')));
  }
}

async function loadOrgData() {
  [state.vehicles, state.drivers] = await Promise.all([api.vehicles(state.org.id), api.drivers(state.org.id)]);
}

// ─── 직원 회원가입 (로그인 없이) ───────────────────────────────────────────
// 아무나 가입하지 못하도록 기업 가입 코드(관리자가 직원에게 알려 준다)와 소속 확인 정보를 받고, 관리자가 승인해야 기록이 보인다.

function renderSignUp() {
  const status = el('div', { class: 'form-status' });
  const button = el('button', { type: 'submit', class: 'button-primary' }, '가입 신청');
  const field = (label, name, props = {}) => el('label', {}, label, el('input', { name, ...props }));
  const form = el('form', { class: 'card login signup', onsubmit: onSubmit },
    el('div', { class: 'brand-mark' }, appMark(), el('h1', {}, '직원 회원가입')),
    el('p', { class: 'muted' }, '회사 차를 쓰는 직원이 자기가 쓰는 차량의 운행기록부와 세무서 제출용 서식을 직접 받는 계정이에요. 회사 관리자가 승인하면 쓸 수 있어요.'),
    field('기업 가입 코드 (8자리, 회사 관리자에게 받으세요) *', 'joinCode', { required: true, autocomplete: 'off', autocapitalize: 'characters', maxlength: '12' }),
    el('p', { class: 'section-label' }, '소속 확인 (관리자에게 보여요)'),
    field('이름 *', 'name', { required: true, maxlength: '40', autocomplete: 'name' }),
    field('부서 *', 'department', { required: true, maxlength: '40', placeholder: '예: 영업팀' }),
    field('사번', 'employeeNo', { maxlength: '20' }),
    field('휴대폰 *', 'phone', { required: true, type: 'tel', maxlength: '20', placeholder: '010-1234-5678', autocomplete: 'tel' }),
    el('p', { class: 'section-label' }, '로그인 정보'),
    field('아이디 (이메일) *', 'email', { required: true, type: 'email', maxlength: '120', autocomplete: 'username' }),
    field('비밀번호 (8자 이상) *', 'password', { required: true, type: 'password', minlength: '8', autocomplete: 'new-password' }),
    field('비밀번호 확인 *', 'passwordConfirm', { required: true, type: 'password', minlength: '8', autocomplete: 'new-password' }),
    el('label', { class: 'consent' },
      el('input', { name: 'consent', type: 'checkbox', required: true }),
      el('span', {}, '[필수] 개인정보 수집·이용 동의 — 수집 항목: 이름, 부서, 사번, 휴대폰, 이메일 · 목적: 소속 확인과 가입 승인, 운행기록부 열람 · 제공: 가입을 신청한 회사의 관리자 · 보관: 회사가 직원 계정을 지울 때까지 · 가입 코드를 틀리면 반복 시도를 막으려고 접속 주소(IP)를 해시로 바꿔 하루 동안 보관'),
    ),
    status,
    button,
    el('p', { class: 'login-signup muted' }, el('button', { type: 'button', class: 'button-text', onclick: () => renderLogin() }, '로그인으로 돌아가기')),
  );
  setScreen(el('main', { class: 'center' }, form));

  async function onSubmit(event) {
    event.preventDefault();
    const data = Object.fromEntries(new FormData(form));
    const values = { ...data, joinCode: normalizeJoinCode(data.joinCode), consent: data.consent === 'on' };
    const problem = signUpProblem(values);
    if (problem) {
      status.replaceChildren(message(problem, 'error'));
      return;
    }
    button.disabled = true;
    try {
      const result = await api.memberSignUp(values);
      if (result.status !== 'OK') {
        status.replaceChildren(message(SIGN_UP_MESSAGES[result.status] ?? result.status, 'error'));
        button.disabled = false;
        return;
      }
      setScreen(el('main', { class: 'center' }, el('section', { class: 'card login' },
        el('div', { class: 'brand-mark' }, appMark(), el('h1', {}, '가입 신청을 보냈어요')),
        el('p', {}, `${result.org_name} 관리자가 승인하면 로그인해서 내가 쓰는 차량의 운행기록부를 받을 수 있어요.`),
        el('button', { class: 'button-primary', onclick: () => renderLogin() }, '로그인으로'),
      )));
    } catch (error) {
      status.replaceChildren(message(error.message, 'error'));
      button.disabled = false;
    }
  }
}

/** 관리자도 승인된 직원도 아닌 계정: 가입 신청 상태를 알려 준다 */
function renderNoAccess(request) {
  const [title, text] = {
    PENDING: ['승인을 기다리고 있어요', '회사 관리자가 가입 신청을 승인하면 내가 쓰는 차량의 운행기록부가 보여요.'],
    REJECTED: ['가입이 승인되지 않았어요', '회사 관리자에게 문의하세요.'],
  }[request?.status] ?? ['볼 수 있는 기업이 없어요', `${api.displayName(api.currentUser())} 계정은 관리자나 직원으로 등록된 기업이 없어요.`];
  setScreen(el('main', { class: 'center' }, el('section', { class: 'card login' },
    el('div', { class: 'brand-mark' }, appMark(), el('h1', {}, title)),
    el('p', {}, text),
    el('button', { class: 'button-secondary', onclick: logout }, '로그아웃'),
  )));
}

async function logout() {
  await api.signOut();
  renderLogin();
}

// ─── 기본 틀 ──────────────────────────────────────────────────────────

function renderMain() {
  const orgPicker = state.orgs.length > 1
    ? el('select', {
      'aria-label': '조직',
      onchange: async (event) => {
        state.org = state.orgs.find((org) => org.id === event.target.value);
        state.vehicleId = '';
        await enter();
      },
    }, state.orgs.map((org) => el('option', { value: org.id, selected: org.id === state.org.id }, org.name)))
    : el('span', { class: 'org-name' }, state.org.name);

  const content = el('section', { class: 'content' });
  setScreen(
    el('header', { class: 'topbar' },
      appMark(),
      el('strong', { class: 'brand' }, '차곡'),
      orgPicker,
      el('span', { class: 'spacer' }),
      el('span', { class: 'user' }, api.displayName(api.currentUser())),
      el('button', { class: 'button-text', onclick: logout }, '로그아웃'),
    ),
    el('nav', { class: 'tabs', role: 'tablist' }, tabsFor(state.role).map((tab) => el('button', {
      role: 'tab',
      'aria-selected': String(tab.id === state.tab),
      class: tab.id === state.tab ? 'tab tab-active' : 'tab',
      onclick: () => {
        state.tab = tab.id;
        renderMain();
      },
    }, tab.label))),
    el('main', {}, content),
  );
  ({
    overview: renderOverview, logbook: renderLogbook, journal: renderJournal, tax: renderTax,
    drivers: renderDrivers, members: renderMembers, vehicles: renderVehicles,
  })[state.tab](content, renderToken);
}

// ─── 운행기록부 ────────────────────────────────────────────────────────

async function renderLogbook(content, token) {
  if (state.role === 'member' && state.vehicles.length === 0) {
    content.replaceChildren(message('아직 배정된 차량이 없어요. 회사 관리자에게 내가 쓰는 차량을 배정해 달라고 하세요.'));
    return;
  }
  const { period } = state;
  // ◀ ▶와 달 칸은 기간 시작 날짜가 든 달을 기준으로 움직인다
  const [year, month] = period.from.split('-').map(Number);
  const now = currentMonth();
  const today = kstParts(Date.now()).date;
  const isCurrentMonth = year === now.year && month === now.month;
  const showMonth = (pickedYear, pickedMonth) => {
    state.period = monthPeriod(pickedYear, pickedMonth);
    renderMain();
  };
  const moveMonth = (delta) => {
    const date = new Date(Date.UTC(year, month - 1 + delta, 1));
    showMonth(date.getUTCFullYear(), date.getUTCMonth() + 1);
  };
  const csvButton = el('button', { class: 'button-primary', disabled: true }, 'CSV 내려받기');
  const body = el('div', {}, message('불러오는 중…'));

  // 날짜 칸 두 개로 기간을 고른다 (사용자 피드백 2026-10-27: 달 단위 말고 날짜로도 고르고 싶다)
  const fromInput = el('input', { type: 'date', 'aria-label': '시작 날짜', value: period.from, max: today });
  const toInput = el('input', { type: 'date', 'aria-label': '끝 날짜', value: period.to, max: today });
  const applyDates = () => {
    const picked = pickPeriod(fromInput.value, toInput.value);
    if (picked.error) {
      body.replaceChildren(message(picked.error));
      return;
    }
    state.period = picked.period;
    renderMain();
  };
  fromInput.addEventListener('change', applyDates);
  toInput.addEventListener('change', applyDates);

  content.replaceChildren(
    el('div', { class: 'toolbar' },
      el('button', { class: 'button-secondary', onclick: () => moveMonth(-1), 'aria-label': '이전 달' }, '◀'),
      el('h2', { class: 'month' }, periodLabel(period)),
      el('button', {
        class: 'button-secondary', onclick: () => moveMonth(1), disabled: isCurrentMonth, 'aria-label': '다음 달',
      }, '▶'),
      // ◀ ▶를 여러 번 누르지 않도록 달을 바로 고른다 (사용자 피드백 2026-10-26)
      el('input', {
        type: 'month',
        'aria-label': '달 고르기',
        value: `${year}-${String(month).padStart(2, '0')}`,
        max: `${now.year}-${String(now.month).padStart(2, '0')}`,
        onchange: (event) => {
          const [pickedYear, pickedMonth] = event.target.value.split('-').map(Number);
          if (!pickedYear || !pickedMonth) return;
          showMonth(pickedYear, pickedMonth);
        },
      }),
      el('span', { class: 'period' }, el('span', { class: 'period-label' }, '기간'), fromInput, '~', toInput),
      el('select', {
        'aria-label': '차량',
        onchange: (event) => {
          state.vehicleId = event.target.value;
          renderMain();
        },
      },
      el('option', { value: '' }, '전체 차량'),
      state.vehicles.map((vehicle) => el('option', { value: vehicle.id, selected: vehicle.id === state.vehicleId }, vehicleName(vehicle)))),
      el('span', { class: 'spacer' }),
      csvButton,
    ),
    body,
  );

  try {
    const [from, to] = periodRange(period);
    const rows = await api.trips(state.org.id, from, to, state.vehicleId || undefined);
    if (token !== renderToken) return;
    const logbook = buildLogbook(rows, names());
    const selected = state.vehicles.find((vehicle) => vehicle.id === state.vehicleId);
    csvButton.disabled = logbook.rows.length === 0;
    csvButton.addEventListener('click', () => download(
      logbookCsv(logbook, { includeVehicle: !selected }),
      csvFileName(period, selected && vehicleName(selected)),
    ));
    body.replaceChildren(logbookSummary(logbook.summary), logbookTable(logbook, !selected, periodMonth(period) != null));
  } catch (error) {
    if (token === renderToken) body.replaceChildren(failure(error) ?? '');
  }
}

function logbookSummary(summary) {
  const card = (label, value, detail) => el('div', { class: 'stat' },
    el('span', { class: 'stat-label' }, label), el('strong', { class: 'stat-value' }, value), detail && el('span', { class: 'stat-detail' }, detail));
  return el('div', { class: 'stats' },
    card('총 주행거리', `${km(summary.totalKm)} km`, `운행 ${summary.tripCount}건`),
    card('업무용 사용거리', `${km(summary.workKm)} km`, `출퇴근 ${km(summary.commuteKm)} · 일반업무 ${km(summary.businessKm)}`),
    card('업무사용비율', summary.workRatioPercent == null ? '-' : `${km(summary.workRatioPercent)}%`),
    card('미분류 운행', `${summary.unclassifiedCount}건`, summary.unclassifiedCount > 0 ? '운전자가 차에서 분류하면 반영돼요' : null),
  );
}

function logbookTable(logbook, includeVehicle, wholeMonth) {
  if (logbook.rows.length === 0) return message(wholeMonth ? '이 달에 기록된 운행이 없어요.' : '이 기간에 기록된 운행이 없어요.');
  const headers = ['사용일자', includeVehicle && '차량', '사용자', '시간(한국)', '주행 전(km)', '주행 후(km)', '주행거리(km)',
    '출퇴근용(km)', '일반업무용(km)', '비고(방문처·업무 내용)', '구분'].filter(Boolean);
  return el('div', { class: 'table-wrap' }, el('table', {},
    el('thead', {}, el('tr', {}, headers.map((header) => el('th', { scope: 'col', class: header.endsWith('(km)') ? 'num' : null }, header)))),
    el('tbody', {}, logbook.rows.map((row) => el('tr', {},
      el('td', {}, `${row.date}(${row.weekday})`),
      includeVehicle && el('td', {}, row.vehicle),
      el('td', {}, row.department ? `${row.driver} · ${row.department}` : row.driver),
      el('td', {}, row.endTime ? `${row.startTime}–${row.endTime}` : row.startTime),
      el('td', { class: 'num' }, km(row.startOdometerKm) || '-'),
      el('td', { class: 'num' }, km(row.endOdometerKm) || '-'),
      el('td', { class: 'num' }, km(row.distanceKm)),
      el('td', { class: 'num' }, km(row.commuteKm)),
      el('td', { class: 'num' }, km(row.businessKm)),
      el('td', {}, row.note ?? ''),
      el('td', {}, el('span', { class: `purpose purpose-${row.purpose.toLowerCase()}` }, PURPOSE_LABELS[row.purpose] ?? row.purpose)),
    ))),
  ));
}

// ─── 현황 ──────────────────────────────────────────────────────────────
// 이번 달 요약, 차량마다 운행을 마칠 때 차가 보고한 상태(하차 점검), 최근 업무 일지

const WINDOW_NAMES = { FRONT_LEFT: '운전석', FRONT_RIGHT: '조수석', REAR_LEFT: '뒷좌석 왼쪽', REAR_RIGHT: '뒷좌석 오른쪽' };
const TIDY_NAMES = { WINDOWS: '창문 닫기', LIGHTS: '실내등 끄기', DOORS: '문 잠그기' };
const LOW_BATTERY = 20;

async function renderOverview(content, token) {
  const now = currentMonth();
  const period = monthPeriod(now.year, now.month);
  const body = el('div', {}, message('불러오는 중…'));
  content.replaceChildren(el('div', { class: 'toolbar' }, el('h2', { class: 'month' }, `${now.year}년 ${now.month}월 현황`)), body);
  try {
    const [from, to] = periodRange(period);
    // 0010 전 서버면 차량 현황 표가 없다 (그때는 안내만 한다)
    const [rows, statuses] = await Promise.all([
      api.trips(state.org.id, from, to),
      api.vehicleStatus(state.org.id).catch(() => null),
    ]);
    if (token !== renderToken) return;
    const logbook = buildLogbook(rows, names());
    const byVehicle = new Map((statuses ?? []).map((status) => [status.vehicle_id, status]));
    const days = workJournal(logbook).slice(0, 3);
    body.replaceChildren(
      logbookSummary(logbook.summary),
      el('h2', {}, '차량 현황'),
      el('p', { class: 'muted section-note' }, '운행을 마칠 때 차가 확인한 상태예요. 운전자가 차 화면의 [한 번에 정리]로 정리한 항목도 함께 보여요.'),
      statuses == null
        ? message('서버에 차량 현황 기능이 아직 없어요 (0010).')
        : state.vehicles.length === 0
          ? message('연결된 차량이 없어요. 차량 탭에서 연결 코드를 발급하세요.')
          : el('div', { class: 'fleet-grid' }, state.vehicles.map((vehicle) => vehicleStatusCard(vehicle, byVehicle.get(vehicle.id), logbook))),
      el('h2', {}, '최근 업무 일지'),
      days.length === 0 ? message('이번 달 일반업무 운행이 아직 없어요.') : journalDays(days),
    );
  } catch (error) {
    if (token === renderToken) body.replaceChildren(failure(error) ?? '');
  }
}

function vehicleStatusCard(vehicle, status, logbook) {
  const monthKm = logbook.rows.filter((row) => row.vehicleId === vehicle.id).reduce((total, row) => total + row.distanceKm, 0);
  const title = el('div', { class: 'vehicle-card-head' },
    el('strong', {}, vehicleName(vehicle)),
    vehicle.plate_number && el('span', { class: 'muted' }, vehicle.plate_number),
  );
  if (!status) {
    return el('article', { class: 'card vehicle-card' }, title,
      el('p', { class: 'muted' }, '아직 운행을 마친 보고가 없어요.'),
      el('p', { class: 'muted' }, `이번 달 ${km(monthKm)} km`));
  }
  const openWindows = status.open_windows ?? [];
  const lowBattery = status.soc_percent != null && status.soc_percent < LOW_BATTERY;
  const issues = [
    openWindows.length > 0 && `창문 열림: ${openWindows.map((window) => WINDOW_NAMES[window] ?? window).join(', ')}`,
    status.cabin_light_on === true && '실내등 켜짐',
    status.doors_locked === false && '문 잠기지 않음',
    lowBattery && `배터리 ${status.soc_percent}% · 충전 필요`,
  ].filter(Boolean);
  const item = (label, value, warn = false) => el('li', { class: warn ? 'warn' : null }, el('span', { class: 'muted' }, label), el('span', {}, value));
  return el('article', { class: `card vehicle-card${issues.length ? ' vehicle-card-warn' : ''}` },
    title,
    el('span', { class: issues.length ? 'chip chip-warn' : 'chip chip-ok' }, issues.length ? `점검 필요 ${issues.length}` : '반납 상태 양호'),
    el('ul', { class: 'status-list' },
      item('마지막 반납', `${formatDateTime(new Date(status.reported_at_ms).toISOString())}${status.driver_name ? ` · ${status.driver_name}` : ''}`),
      item('배터리', status.soc_percent == null ? '-' : `${status.soc_percent}%`, lowBattery),
      item('창문', openWindows.length ? openWindows.map((window) => WINDOW_NAMES[window] ?? window).join(', ') + ' 열림' : '모두 닫힘', openWindows.length > 0),
      item('문', status.doors_locked == null ? '확인 못 함' : status.doors_locked ? '잠김' : '열림', status.doors_locked === false),
      item('실내등', status.cabin_light_on == null ? '확인 못 함' : status.cabin_light_on ? '켜짐' : '꺼짐', status.cabin_light_on === true),
      item('계기판', status.odometer_km == null ? '-' : `${km(Number(status.odometer_km))} km`),
      item('이번 달', `${km(monthKm)} km`),
    ),
    (status.tidied ?? []).length > 0 && el('p', { class: 'muted' },
      `운전자가 차 화면에서 정리함: ${Object.keys(TIDY_NAMES).filter((tidy) => status.tidied.includes(tidy)).map((tidy) => TIDY_NAMES[tidy]).join(', ')}`),
  );
}

// ─── 업무 일지 ─────────────────────────────────────────────────────────
// 일반업무 운행을 날짜별로: 언제·누가·어느 차로·어디서·무슨 일 (운전자가 차에서 말로 남긴 업무 내용 포함)

async function renderJournal(content, token) {
  const { period } = state;
  const [year, month] = period.from.split('-').map(Number);
  const now = currentMonth();
  const csvButton = el('button', { class: 'button-primary', disabled: true }, 'CSV 내려받기');
  const body = el('div', {}, message('불러오는 중…'));
  content.replaceChildren(
    el('div', { class: 'toolbar' },
      el('h2', { class: 'month' }, periodLabel(period)),
      el('input', {
        type: 'month',
        'aria-label': '달 고르기',
        value: `${year}-${String(month).padStart(2, '0')}`,
        max: `${now.year}-${String(now.month).padStart(2, '0')}`,
        onchange: (event) => {
          const [pickedYear, pickedMonth] = event.target.value.split('-').map(Number);
          if (!pickedYear || !pickedMonth) return;
          state.period = monthPeriod(pickedYear, pickedMonth);
          renderMain();
        },
      }),
      el('select', {
        'aria-label': '차량',
        onchange: (event) => {
          state.vehicleId = event.target.value;
          renderMain();
        },
      },
      el('option', { value: '' }, '전체 차량'),
      state.vehicles.map((vehicle) => el('option', { value: vehicle.id, selected: vehicle.id === state.vehicleId }, vehicleName(vehicle)))),
      el('span', { class: 'spacer' }),
      csvButton,
    ),
    el('p', { class: 'muted section-note' }, '일반업무로 기록된 운행만 모았어요. 업무 내용은 운전자가 정차 후 차에 말하거나 Gleo AI에 말한 내용이에요.'),
    body,
  );
  try {
    const [from, to] = periodRange(period);
    const rows = await api.trips(state.org.id, from, to, state.vehicleId || undefined);
    if (token !== renderToken) return;
    const days = workJournal(buildLogbook(rows, names()));
    csvButton.disabled = days.length === 0;
    csvButton.addEventListener('click', () => download(workJournalCsv(days), workJournalFileName(period)));
    body.replaceChildren(days.length === 0 ? message('이 기간에 일반업무 운행이 없어요.') : journalDays(days));
  } catch (error) {
    if (token === renderToken) body.replaceChildren(failure(error) ?? '');
  }
}

function journalDays(days) {
  return el('div', { class: 'journal' }, days.map((day) => el('section', { class: 'journal-day' },
    el('h3', {}, `${day.date}(${day.weekday})`, el('span', { class: 'muted' }, ` · ${day.entries.length}건 · ${km(day.km)} km`)),
    el('div', { class: 'table-wrap' }, el('table', {},
      el('thead', {}, el('tr', {}, ['시간', '운전자', '차량', '방문처', '업무 내용', '주행거리(km)'].map((header) => el('th', { scope: 'col', class: header.endsWith('(km)') ? 'num' : null }, header)))),
      el('tbody', {}, [...day.entries].sort((a, b) => a.startTime.localeCompare(b.startTime)).map((row) => el('tr', {},
        el('td', {}, row.endTime ? `${row.startTime}–${row.endTime}` : row.startTime),
        el('td', {}, row.department ? `${row.driver} · ${row.department}` : row.driver),
        el('td', {}, row.vehicle),
        el('td', {}, row.visitNote ?? '-'),
        el('td', { class: row.workNote ? 'work-note' : 'muted' }, row.workNote ?? '-'),
        el('td', { class: 'num' }, km(row.distanceKm)),
      ))),
    )),
  )));
}

// ─── 운전자 ────────────────────────────────────────────────────────────
// 공용차 운전자 명단: 승인된 직원은 차가 명단을 받을 때 자동으로 들어가고, 계정이 없는 운전자는 여기서 추가한다.
// 맞춤 설정(온도·시트 열선·무드등)은 운전자가 차에서 마지막으로 쓴 값을 차가 올린다.

function comfortSummary(comfort) {
  if (!comfort || Object.keys(comfort).length === 0) return el('span', { class: 'muted' }, '아직 없음');
  const parts = [];
  if (comfort.tempC != null) parts.push(`${comfort.tempC}°`);
  if (comfort.seatHeat != null) parts.push(comfort.seatHeat > 0 ? `열선 ${comfort.seatHeat}` : comfort.seatHeat < 0 ? `통풍 ${-comfort.seatHeat}` : '열선 끔');
  const node = el('span', { class: 'comfort' }, parts.join(' · '));
  if (/^#[0-9A-Fa-f]{6}$/.test(comfort.ambientColor ?? '')) {
    // 운전자가 차에서 고른 무드등 색 그대로 (화면 색 토큰이 아니라 차량 데이터). CSP 때문에 style 속성 대신 CSSOM으로 넣는다
    const dot = el('span', { class: 'ambient-dot', title: `무드등 ${comfort.ambientColor}` });
    dot.style.backgroundColor = comfort.ambientColor;
    node.append(dot);
  }
  return node;
}

function renderDrivers(content, token) {
  const status = el('div', { class: 'form-status' });
  // 차량을 연결할 때 차마다 하나씩 생기는 기본 운전자: 어느 차의 것인지 보여 준다
  const defaults = new Map(state.vehicles.filter((vehicle) => vehicle.default_driver_id).map((vehicle) => [vehicle.default_driver_id, vehicleName(vehicle)]));
  const form = el('form', { class: 'card driver-form', onsubmit: onAdd },
    el('h2', {}, '운전자 추가'),
    el('p', { class: 'muted' }, '로그인 계정이 없는 운전자를 추가해요. 직원 회원가입으로 승인된 직원은 자동으로 들어가요. 차의 운전자 명단에는 10분 안에 나타나요.'),
    el('div', { class: 'driver-form-row' },
      el('label', {}, '이름 *', el('input', { name: 'name', required: true, maxlength: '40' })),
      el('label', {}, '부서', el('input', { name: 'department', maxlength: '40', placeholder: '예: 영업1팀' })),
      el('button', { type: 'submit', class: 'button-primary' }, '추가'),
    ),
    status,
  );
  const visible = state.drivers.filter((driver) => !driver.archived);
  const hidden = state.drivers.filter((driver) => driver.archived);
  const rowOf = (driver) => el('tr', {},
    el('td', {}, driver.name),
    el('td', {}, driver.department ?? '-'),
    el('td', {}, driver.user_id ? '직원 계정' : defaults.has(driver.id) ? `차량 기본 (${defaults.get(driver.id)})` : '운전자'),
    el('td', {}, comfortSummary(driver.comfort)),
    el('td', { class: 'actions' }, el('button', {
      class: driver.archived ? 'button-text' : 'button-text danger',
      onclick: () => toggle(driver),
    }, driver.archived ? '다시 보이기' : '숨기기')),
  );
  const table = (drivers) => el('div', { class: 'table-wrap' }, el('table', {},
    el('thead', {}, el('tr', {}, ['이름', '부서', '구분', '맞춤 설정 (차가 기억한 값)', ''].map((header) => el('th', { scope: 'col' }, header)))),
    el('tbody', {}, drivers.map(rowOf)),
  ));
  content.replaceChildren(...[
    form,
    el('h2', {}, `차에서 고를 수 있는 운전자 ${visible.length}명`),
    visible.length ? table(visible) : message('아직 운전자가 없어요.'),
    hidden.length > 0 && el('h2', {}, `숨긴 운전자 ${hidden.length}명`),
    hidden.length > 0 && table(hidden),
  ].filter(Boolean));

  async function onAdd(event) {
    event.preventDefault();
    const data = Object.fromEntries(new FormData(form));
    const name = data.name.trim();
    if (!name) return;
    try {
      await api.addDriver(state.org.id, name, data.department.trim());
      await loadOrgData();
      if (token === renderToken) renderMain();
    } catch (error) {
      status.replaceChildren(failure(error) ?? '');
    }
  }

  async function toggle(driver) {
    try {
      await api.setDriverArchived(driver.id, !driver.archived);
      await loadOrgData();
      if (token === renderToken) renderMain();
    } catch (error) {
      status.replaceChildren(failure(error) ?? '');
    }
  }
}

// ─── 세무 제출 ─────────────────────────────────────────────────────────
// 운행기록부는 작성해 보관하다가 세무서가 요구하면 제출한다 (국세청고시 제2025-8호).
// 법인세 신고 때 내는 업무용승용차 관련비용 명세서에는 차량별 업무사용비율 같은 운행기록 값을 옮겨 적는다.

const taxVehicle = (vehicle) => ({ id: vehicle.id, name: vehicleName(vehicle), model: vehicle.model, plateNumber: vehicle.plate_number });

async function renderTax(content, token) {
  const period = state.taxPeriod;
  const fromInput = el('input', { type: 'date', 'aria-label': '사업연도 시작', value: period.from });
  const toInput = el('input', { type: 'date', 'aria-label': '사업연도 끝', value: period.to });
  const csvButton = el('button', { class: 'button-secondary', disabled: true }, '명세서용 값 CSV');
  const printButton = el('button', { class: 'button-primary', disabled: true }, '서식 전체 보기 · 인쇄');
  const body = el('div', {}, message('불러오는 중…'));
  const applyDates = () => {
    const picked = pickPeriod(fromInput.value, toInput.value);
    if (picked.error) {
      body.replaceChildren(message(picked.error));
      return;
    }
    state.taxPeriod = picked.period;
    renderMain();
  };
  fromInput.addEventListener('change', applyDates);
  toInput.addEventListener('change', applyDates);

  content.replaceChildren(
    el('div', { class: 'card tax-intro' },
      el('h2', {}, '세무서 제출용 운행기록부'),
      el('p', {}, '운행기록부는 작성해 보관하다가 세무서가 요구하면 제출해요. 아래 서식은 국세청고시 제2025-8호 별지 「업무용승용차 운행기록부」와 같은 칸으로 만들어요.'),
      el('p', { class: 'muted' }, '법인세 신고 때 내는 업무용승용차 관련비용 명세서에는 아래 표의 차량별 총주행 거리·업무용 사용거리·업무사용비율을 옮겨 적으세요.'),
    ),
    el('div', { class: 'toolbar' },
      el('span', { class: 'period' }, el('span', { class: 'period-label' }, '사업연도'), fromInput, '~', toInput),
      el('span', { class: 'spacer' }),
      csvButton,
      printButton,
    ),
    body,
  );

  try {
    const [from, to] = periodRange(period);
    const rows = await api.trips(state.org.id, from, to);
    if (token !== renderToken) return;
    const byVehicle = logbooksByVehicle(state.vehicles.map(taxVehicle), rows, names());
    if (byVehicle.length === 0) {
      body.replaceChildren(message(state.role === 'member' ? '아직 배정된 차량이 없어요. 회사 관리자에게 요청하세요.' : '아직 연결된 차량이 없어요.'));
      return;
    }
    csvButton.disabled = false;
    printButton.disabled = false;
    csvButton.addEventListener('click', () => download(statementCsv(byVehicle, period), statementFileName(period)));
    printButton.addEventListener('click', () => openTaxForms(byVehicle, period));
    const missing = byVehicle.filter(({ vehicle }) => !vehicle.model || !vehicle.plateNumber).length;
    body.replaceChildren(
      missing > 0 ? message(`차종이나 자동차등록번호가 비어 있는 차량이 ${missing}대 있어요. 차량 탭에서 적으면 서식에 들어가요.`) : '',
      state.org.businessNumber ? '' : message('기업 정보에 사업자등록번호가 없어 서식의 그 칸이 비어요.'),
      statementTable(byVehicle, period),
    );
  } catch (error) {
    if (token === renderToken) body.replaceChildren(failure(error) ?? '');
  }
}

function statementTable(byVehicle, period) {
  const headers = ['차량', '차종', '자동차등록번호', '운행', '총주행 거리(km)', '업무용 사용거리(km)', '업무사용비율', ''];
  return el('div', { class: 'table-wrap' }, el('table', {},
    el('thead', {}, el('tr', {}, headers.map((header) => el('th', { scope: 'col', class: header.endsWith('(km)') ? 'num' : null }, header)))),
    el('tbody', {}, byVehicle.map((entry) => {
      const { vehicle, logbook: { summary } } = entry;
      return el('tr', {},
        el('td', {}, vehicle.name),
        el('td', {}, vehicle.model ?? el('span', { class: 'muted' }, '없음')),
        el('td', {}, vehicle.plateNumber ?? el('span', { class: 'muted' }, '없음')),
        el('td', {}, `${summary.tripCount}건`),
        el('td', { class: 'num' }, km(summary.totalKm)),
        el('td', { class: 'num' }, km(summary.workKm)),
        el('td', {}, summary.workRatioPercent == null ? '-' : `${km(summary.workRatioPercent)}%`),
        el('td', { class: 'actions' }, el('button', { class: 'button-text', onclick: () => openTaxForms([entry], period) }, '서식 보기')),
      );
    })),
  ));
}

/** 서식 미리보기. 종이 서식이라 라이트 테마 토큰으로 보여 주고, 인쇄하면 서식만 A4 가로로 나온다 */
function openTaxForms(entries, period, org = state.org) {
  const root = document.documentElement;
  const previousTheme = root.dataset.theme;
  root.dataset.theme = 'light';
  document.body.classList.add('printing-forms');
  const close = () => {
    overlay.remove();
    document.body.classList.remove('printing-forms');
    root.dataset.theme = previousTheme;
    document.removeEventListener('keydown', onKey);
  };
  const onKey = (event) => {
    if (event.key === 'Escape') close();
  };
  const overlay = el('div', { class: 'print-preview', role: 'dialog', 'aria-modal': 'true', 'aria-label': '세무서 제출용 운행기록부' },
    el('div', { class: 'print-toolbar' },
      el('strong', {}, `업무용승용차 운행기록부 ${entries.length}장`),
      el('span', { class: 'muted' }, `사업연도 ${formDate(period.from)} ~ ${formDate(period.to)}`),
      el('span', { class: 'spacer' }),
      el('button', { class: 'button-primary', onclick: () => window.print() }, '인쇄 · PDF로 저장'),
      el('button', { class: 'button-secondary', onclick: close }, '닫기'),
    ),
    entries.map((entry) => taxFormPage(entry, period, org)),
  );
  document.addEventListener('keydown', onKey);
  document.body.append(overlay);
}

/** 국세청고시 제2025-8호 별지 「업무용승용차 운행기록부」와 같은 칸 배치 (차량 한 대) */
function taxFormPage({ vehicle, logbook }, period, org) {
  const { summary } = logbook;
  const th = (text, props = {}) => el('th', { scope: 'col', ...props }, text);
  const blank = (props = {}) => el('td', { class: 'tax-shade', ...props });
  return el('article', { class: 'tax-form' },
    el('table', { class: 'tax-head' }, el('tbody', {},
      el('tr', {},
        el('th', { rowspan: '2', scope: 'row' }, '사업연도'),
        el('td', { rowspan: '2', class: 'tax-period' }, formDate(period.from), el('br'), '~', el('br'), formDate(period.to)),
        el('td', { rowspan: '2', class: 'tax-title' }, '업무용승용차 운행기록부'),
        el('th', { scope: 'row' }, '법인명'),
        el('td', {}, org.name),
      ),
      el('tr', {}, el('th', { scope: 'row' }, '사업자등록번호'), el('td', {}, org.businessNumber ?? '')),
    )),
    el('h3', {}, '1. 기본정보'),
    el('table', { class: 'tax-basic' },
      el('thead', {}, el('tr', {}, th('①차 종'), th('②자동차등록번호'))),
      el('tbody', {}, el('tr', {}, el('td', {}, vehicle.model ?? ''), el('td', {}, vehicle.plateNumber ?? ''))),
    ),
    el('h3', {}, '2. 업무용 사용비율 계산'),
    el('table', { class: 'tax-rows' },
      el('thead', {},
        el('tr', {}, th('③사용 일자 (요일)', { rowspan: '3' }), th('④사용자', { colspan: '2' }), th('운 행 내 역', { colspan: '6' })),
        el('tr', {},
          th('부서', { rowspan: '2' }), th('성명', { rowspan: '2' }),
          th('⑤주행 전 계기판의 거리(㎞)', { rowspan: '2' }), th('⑥주행 후 계기판의 거리(㎞)', { rowspan: '2' }),
          th('⑦주행거리(㎞)', { rowspan: '2' }), th('업무용 사용거리(㎞)', { colspan: '2' }), th('⑩비 고', { rowspan: '2' }),
        ),
        el('tr', {}, th('⑧출·퇴근용(㎞)'), th('⑨일반 업무용(㎞)')),
      ),
      el('tbody', {}, logbook.rows.map((row) => el('tr', {},
        el('td', {}, `${row.date}(${row.weekday})`),
        el('td', {}, row.department ?? ''),
        el('td', {}, row.driver),
        el('td', { class: 'num' }, km(row.startOdometerKm)),
        el('td', { class: 'num' }, km(row.endOdometerKm)),
        el('td', { class: 'num' }, km(row.distanceKm)),
        el('td', { class: 'num' }, km(row.commuteKm)),
        el('td', { class: 'num' }, km(row.businessKm)),
        el('td', {}, taxFormNote(row)),
      ))),
      el('tfoot', {},
        el('tr', {}, blank({ rowspan: '2' }), blank({ rowspan: '2', colspan: '2' }),
          th('⑪사업연도 총주행 거리(㎞)', { colspan: '3' }), th('⑫사업연도 업무용 사용거리(㎞)', { colspan: '2' }), th('⑬업무사용비율(⑫/⑪)')),
        el('tr', {},
          el('td', { colspan: '3', class: 'num' }, km(summary.totalKm)),
          el('td', { colspan: '2', class: 'num' }, km(summary.workKm)),
          el('td', { class: 'num' }, summary.workRatioPercent == null ? '' : `${km(summary.workRatioPercent)}%`),
        ),
      ),
    ),
    el('p', { class: 'tax-source' }, '국세청고시 제2025-8호 별지 서식과 같은 칸 · 차곡(자동 운행기록부)가 차량 계기판 거리로 작성'),
  );
}

// ─── 휴대폰으로 받기 (차 화면의 QR 코드로 열린다, 로그인 없음) ─────────────────
// 차에는 메일·메신저 앱이 없어서, 운전자가 휴대폰에서 운행기록부를 열어 저장·보낸다. 링크는 10분 동안만 열린다.

async function renderShare({ token, period: carPeriod }) {
  const content = el('section', { class: 'content share' }, message('운행기록부를 불러오는 중…'));
  setScreen(el('header', { class: 'topbar' }, appMark(), el('strong', { class: 'brand' }, '차곡')), el('main', {}, content));
  try {
    const shared = await api.sharedLogbook(token);
    if (shared.status !== 'OK') {
      content.replaceChildren(
        el('h1', {}, '링크가 만료됐어요'),
        el('p', {}, '휴대폰으로 받기 링크는 10분 동안만 열려요. 차의 운행기록부에서 [휴대폰으로 받기]를 다시 눌러 새 QR 코드를 찍으세요.'),
      );
      return;
    }
    // 기간 이름은 차에서 고른 날짜로 (차와 휴대폰의 시간대가 달라도 같은 이름). 주소에 없으면 서버 범위를 한국 날짜로
    const period = carPeriod ?? periodFromRange(shared.from_ms, shared.to_ms);
    const vehicle = {
      id: shared.vehicle.id,
      name: shared.vehicle.display_name || shared.vehicle.vin || '차량',
      model: shared.vehicle.model,
      plateNumber: shared.vehicle.plate_number,
    };
    const org = { name: shared.org.name, businessNumber: shared.org.business_number };
    const logbook = buildLogbook(shared.trips, { drivers: shared.drivers, vehicles: { [vehicle.id]: vehicle.name } });
    const csv = logbookCsv(logbook);
    const fileName = csvFileName(period, vehicle.name);
    content.replaceChildren(
      el('h1', {}, `${vehicle.name} 운행기록부`),
      el('p', { class: 'muted' }, `${org.name} · ${periodLabel(period)}`),
      el('div', { class: 'toolbar' },
        el('button', { class: 'button-primary', onclick: () => download(csv, fileName) }, 'CSV 저장'),
        shareFileButton(csv, fileName, `${vehicle.name} 운행기록부 ${periodLabel(period)}`),
        el('button', { class: 'button-secondary', onclick: () => openTaxForms([{ vehicle, logbook }], period, org) }, '세무서 제출용 서식 · PDF'),
      ),
      logbookSummary(logbook.summary),
      logbookTable(logbook, false, periodMonth(period) != null),
      el('p', { class: 'muted' }, `차에서 만든 링크로 열린 페이지예요 (${formatDateTime(shared.expires_at)}까지). 저장한 파일은 이 휴대폰에만 남아요.`),
    );
  } catch (error) {
    content.replaceChildren(message(error.message, 'error'));
  }
}

/** 휴대폰의 공유(카톡·메일 등)로 CSV 파일을 보낸다. 브라우저가 파일 공유를 못 하면 버튼을 넣지 않는다 */
function shareFileButton(csv, fileName, title) {
  const file = new File([csv], fileName, { type: 'text/csv' });
  if (!navigator.canShare?.({ files: [file] })) return null;
  return el('button', {
    class: 'button-secondary', onclick: () => navigator.share({ files: [file], title }).catch(() => {}),
  }, '보내기 (카톡·메일)');
}

// ─── 직원 (관리자) ──────────────────────────────────────────────────────
// 직원이 웹에 직접 가입해 자기 차량의 기록을 받는다. 관리자는 가입 코드를 알려 주고, 신청을 한 번 승인하면서 쓰는 차량을 고른다.

function renderMembers(content, token) {
  const codeBox = el('div', { class: 'card join-code-card' }, message('불러오는 중…'));
  const requestsBox = el('div', {}, message('불러오는 중…'));
  const membersBox = el('div', {});
  const rejectedBox = el('div', {});
  const status = el('div', { class: 'form-status' });
  content.replaceChildren(codeBox, status, el('h2', {}, '가입 신청'), requestsBox, el('h2', {}, '직원'), membersBox, rejectedBox);
  load();

  async function load() {
    try {
      const [code, requests, assignments] = await Promise.all([
        api.joinCode(state.org.id), api.memberRequests(state.org.id), api.vehicleMembers(state.org.id),
      ]);
      if (token !== renderToken) return;
      showCode(code);
      const pending = requests.filter((request) => request.status === 'PENDING');
      const approved = requests.filter((request) => request.status === 'APPROVED');
      // 거절했거나 내보낸 신청도 다시 승인할 수 있다 (같은 이메일로는 다시 가입할 수 없어서)
      const rejected = requests.filter((request) => request.status === 'REJECTED');
      requestsBox.replaceChildren(pending.length === 0
        ? message('새 가입 신청이 없어요.')
        : el('div', { class: 'table-wrap' }, el('table', {},
          el('thead', {}, el('tr', {}, ['신청일', '이름', '부서', '사번', '휴대폰', '쓰는 차량', ''].map((header) => el('th', { scope: 'col' }, header)))),
          el('tbody', {}, pending.map((request) => requestRow(request))),
        )));
      membersBox.replaceChildren(approved.length === 0
        ? message('아직 승인한 직원이 없어요.')
        : el('div', { class: 'table-wrap' }, el('table', {},
          el('thead', {}, el('tr', {}, ['이름', '부서', '사번', '휴대폰', '쓰는 차량', ''].map((header) => el('th', { scope: 'col' }, header)))),
          el('tbody', {}, approved.map((member) => memberRow(member, assignments))),
        )));
      rejectedBox.replaceChildren(...(rejected.length === 0 ? [] : [
        el('h2', {}, '거절·내보낸 신청'),
        el('p', { class: 'muted' }, '잘못 거절했거나 다시 일하게 된 직원은 여기서 다시 승인하세요.'),
        el('div', { class: 'table-wrap' }, el('table', {},
          el('thead', {}, el('tr', {}, ['신청일', '이름', '부서', '사번', '휴대폰', '쓰는 차량', ''].map((header) => el('th', { scope: 'col' }, header)))),
          el('tbody', {}, rejected.map((request) => requestRow(request, { again: true }))),
        )),
      ]));
    } catch (error) {
      if (token === renderToken) requestsBox.replaceChildren(failure(error) ?? '');
    }
  }

  function showCode(code) {
    codeBox.replaceChildren(
      el('div', {},
        el('span', { class: 'muted' }, '기업 가입 코드 '),
        el('strong', { class: 'code', 'aria-label': code.split('').join(' ') }, `${code.slice(0, 4)} ${code.slice(4)}`),
      ),
      el('p', { class: 'muted' }, '직원에게 이 코드를 알려 주세요. 직원은 로그인 화면의 [직원 회원가입]에서 이 코드와 소속 정보를 적어 신청하고, 여기서 승인하면 고른 차량의 운행기록부를 직접 받아요. 코드가 밖으로 새면 새로 만드세요 (이미 가입한 직원은 그대로).'),
      el('button', { class: 'button-secondary', onclick: regenerate }, '새 코드 만들기'),
    );
  }

  async function regenerate() {
    try {
      showCode(await api.regenerateJoinCode(state.org.id));
    } catch (error) {
      status.replaceChildren(failure(error) ?? '');
    }
  }

  /** 차량 고르기 (체크 상자) */
  function vehiclePicker(selected) {
    if (state.vehicles.length === 0) return { node: el('span', { class: 'muted' }, '연결된 차량 없음'), value: () => [] };
    const boxes = state.vehicles.map((vehicle) => el('input', { type: 'checkbox', value: vehicle.id, checked: selected.includes(vehicle.id) }));
    const node = el('div', { class: 'vehicle-picker' },
      state.vehicles.map((vehicle, index) => el('label', { class: 'check' }, boxes[index], vehicleName(vehicle))));
    return { node, value: () => boxes.filter((box) => box.checked).map((box) => box.value) };
  }

  function requestRow(request, { again = false } = {}) {
    const picker = vehiclePicker(state.vehicles.length === 1 ? [state.vehicles[0].id] : []);
    const decide = async (approve) => {
      try {
        await api.decideMemberRequest(request.id, approve, approve ? picker.value() : []);
        if (token === renderToken) load();
      } catch (error) {
        status.replaceChildren(failure(error) ?? '');
      }
    };
    return el('tr', {},
      el('td', {}, formatDateTime(request.created_at)),
      el('td', {}, request.name),
      el('td', {}, request.department),
      el('td', {}, request.employee_no ?? '-'),
      el('td', {}, request.phone),
      el('td', {}, picker.node),
      el('td', { class: 'actions' },
        el('button', { class: again ? 'button-secondary' : 'button-primary', onclick: () => decide(true) }, again ? '다시 승인' : '승인'),
        !again && el('button', { class: 'button-text danger', onclick: () => decide(false) }, '거절'),
      ),
    );
  }

  function memberRow(member, assignments) {
    const picker = vehiclePicker(assignments.filter((row) => row.user_id === member.user_id).map((row) => row.vehicle_id));
    const save = async () => {
      try {
        await api.setMemberVehicles(member.user_id, state.org.id, picker.value());
        status.replaceChildren(message(`${member.name} 직원의 차량을 저장했어요.`, 'success'));
      } catch (error) {
        status.replaceChildren(failure(error) ?? '');
      }
    };
    const remove = async () => {
      if (!window.confirm(`${member.name} 직원을 내보낼까요? 그 뒤로는 운행기록부를 볼 수 없어요.`)) return;
      try {
        await api.removeMember(member.user_id, state.org.id);
        if (token === renderToken) load();
      } catch (error) {
        status.replaceChildren(failure(error) ?? '');
      }
    };
    return el('tr', {},
      el('td', {}, member.name),
      el('td', {}, member.department),
      el('td', {}, member.employee_no ?? '-'),
      el('td', {}, member.phone),
      el('td', {}, picker.node),
      el('td', { class: 'actions' },
        el('button', { class: 'button-secondary', onclick: save }, '차량 저장'),
        el('button', { class: 'button-text danger', onclick: remove }, '내보내기'),
      ),
    );
  }
}

// ─── 차량 ─────────────────────────────────────────────────────────────

function renderVehicles(content, token) {
  content.replaceChildren(pairingSection(token), el('h2', {}, '연결된 차량'), vehicleList(token));
}

function vehicleList(token) {
  if (state.vehicles.length === 0) {
    return message('아직 연결된 차량이 없어요. 위에서 연결 코드를 발급해 차에서 입력하세요.');
  }
  const status = el('div', { class: 'form-status' });
  return el('div', {}, status,
    el('p', { class: 'muted' }, '차종과 자동차등록번호는 세무서 제출용 운행기록부 서식의 기본정보예요 (국세청고시 제2025-8호).'),
    el('div', { class: 'table-wrap' }, el('table', {},
      el('thead', {}, el('tr', {}, ['차량 이름', '차종', '자동차등록번호', 'VIN', '기본 운전자', '연결 상태', '']
        .map((header) => el('th', { scope: 'col' }, header)))),
      el('tbody', {}, state.vehicles.map(vehicleRow)),
    )));

  function vehicleRow(vehicle) {
    const model = el('input', {
      value: vehicle.model ?? '', maxlength: '40', placeholder: '예: 아이오닉 5', 'aria-label': `${vehicleName(vehicle)} 차종`,
    });
    const plate = el('input', {
      value: vehicle.plate_number ?? '', maxlength: '20', placeholder: '예: 12가3456', 'aria-label': `${vehicleName(vehicle)} 자동차등록번호`,
    });
    const save = el('button', { class: 'button-secondary', disabled: true, onclick: () => saveRegistration(vehicle, model, plate, save) }, '저장');
    const changed = () => {
      save.disabled = model.value.trim() === (vehicle.model ?? '') && plate.value.trim() === (vehicle.plate_number ?? '');
    };
    model.addEventListener('input', changed);
    plate.addEventListener('input', changed);
    return el('tr', {},
      el('td', { title: `차량 ID: ${vehicle.id}` }, vehicleName(vehicle)),
      el('td', {}, model),
      el('td', {}, plate),
      el('td', {}, vehicle.vin ?? el('span', { class: 'muted' }, 'VIN 미확인 (기기 ID로 구분)')),
      el('td', {}, driverName(vehicle.default_driver_id)),
      el('td', {}, vehicle.device_user_id ? `연결됨 · ${formatDateTime(vehicle.paired_at)}` : el('span', { class: 'muted' }, '연결 해제됨')),
      el('td', { class: 'actions' }, save, vehicle.device_user_id && el('button', {
        class: 'button-text danger', onclick: () => unpair(vehicle),
      }, '연결 해제')),
    );
  }

  async function saveRegistration(vehicle, model, plate, button) {
    button.disabled = true;
    try {
      await api.updateVehicleRegistration(vehicle.id, { model: model.value.trim(), plateNumber: plate.value.trim() });
      await loadOrgData();
      if (token !== renderToken) return;
      renderMain();
    } catch (error) {
      button.disabled = false;
      status.replaceChildren(failure(error) ?? '');
    }
  }

  async function unpair(vehicle) {
    const ok = window.confirm(`'${vehicleName(vehicle)}' 차량의 연결을 해제할까요?\n운행 기록은 그대로 남아요. 다시 기록을 받으려면 차에서 연결 코드를 새로 입력해야 해요.`);
    if (!ok) return;
    try {
      await api.unpairVehicle(vehicle.id);
      await loadOrgData();
      if (token === renderToken) renderMain();
    } catch (error) {
      status.replaceChildren(failure(error) ?? '');
    }
  }
}

// ─── 연결 코드 발급 (차량 탭 위쪽) ────────────────────────────────────────

function pairingSection(token) {
  const result = el('div', { class: 'issued' });
  const list = el('div', {}, message('불러오는 중…'));
  const button = el('button', { type: 'submit', class: 'button-primary' }, '코드 발급');
  const form = el('form', { class: 'card pairing-form', onsubmit: onSubmit },
    el('h2', {}, '연결 코드 발급'),
    el('p', { class: 'muted' }, '차가 정차한 상태에서 앱의 연결 화면에 입력하는 6자리 코드예요. 10분 동안 한 번만 쓸 수 있어요.'),
    el('label', {}, '차량 이름 (선택)', el('input', { name: 'vehicleName', maxlength: '40', placeholder: '예: 영업 1호차' })),
    button,
  );
  loadCodes();
  return el('section', {}, el('div', { class: 'pairing' }, form, result), el('h2', {}, '사용할 수 있는 코드'), list);

  async function onSubmit(event) {
    event.preventDefault();
    button.disabled = true;
    try {
      const issued = await api.issuePairingCode(state.org.id, String(new FormData(form).get('vehicleName')).trim());
      if (token !== renderToken) return;
      form.reset();
      showIssued(issued);
      loadCodes();
    } catch (error) {
      if (token === renderToken) result.replaceChildren(failure(error) ?? '');
    } finally {
      button.disabled = false;
    }
  }

  function showIssued(issued) {
    const remaining = el('p', { class: 'countdown' });
    const expiresAt = Date.parse(issued.expires_at);
    result.replaceChildren(el('div', { class: 'card code-card' },
      el('span', { class: 'muted' }, '발급한 코드'),
      el('strong', { class: 'code', 'aria-label': issued.code.split('').join(' ') }, `${issued.code.slice(0, 3)} ${issued.code.slice(3)}`),
      remaining,
    ));
    const tick = () => {
      const left = Math.max(0, Math.round((expiresAt - Date.now()) / 1000));
      remaining.textContent = left > 0
        ? `${Math.floor(left / 60)}:${String(left % 60).padStart(2, '0')} 남음`
        : '만료됐어요. 새 코드를 발급하세요.';
      if (left === 0) clearInterval(timer);
    };
    clearInterval(timer);
    tick();
    timer = setInterval(tick, 1000);
  }

  async function loadCodes() {
    try {
      const codes = await api.activePairingCodes(state.org.id);
      if (token !== renderToken) return;
      list.replaceChildren(codes.length === 0 ? message('지금 쓸 수 있는 코드가 없어요.') : el('div', { class: 'table-wrap' }, el('table', {},
        el('thead', {}, el('tr', {}, ['코드', '차량 이름', '만료', '종류'].map((header) => el('th', { scope: 'col' }, header)))),
        el('tbody', {}, codes.map((code) => el('tr', {},
          el('td', { class: 'code-cell' }, code.code),
          el('td', {}, code.vehicle_name ?? '-'),
          el('td', {}, formatDateTime(code.expires_at)),
          el('td', {}, code.reusable ? '여러 번 사용 (시연용)' : '1회용'),
        ))),
      )));
    } catch (error) {
      if (token === renderToken) list.replaceChildren(failure(error) ?? '');
    }
  }
}

// ─── 개발자: 기업 등록 ────────────────────────────────────────────────

const ORG_RESULT_MESSAGES = {
  INVALID_NAME: '기업명을 입력하세요.',
  INVALID_EMAIL: '기업 관리자 아이디는 이메일 형식이어야 해요.',
  WEAK_PASSWORD: '초기 비밀번호는 8자 이상이어야 해요.',
  EMAIL_TAKEN: '이미 쓰고 있는 아이디예요. 다른 아이디를 넣어 주세요.',
};

/** 개발자 화면: 기업 정보를 적어 등록하고(기업 관리자 계정도 함께 만든다), 등록된 기업을 본다 */
function renderDeveloper() {
  const content = el('section', { class: 'content' });
  setScreen(
    el('header', { class: 'topbar' },
      appMark(),
      el('strong', { class: 'brand' }, '차곡'),
      el('span', { class: 'badge badge-developer' }, '개발자'),
      el('span', { class: 'spacer' }),
      el('span', { class: 'user' }, api.displayName(api.currentUser())),
      el('button', { class: 'button-text', onclick: logout }, '로그아웃'),
    ),
    el('main', {}, content),
  );
  const token = renderToken;
  const list = el('div', {}, message('불러오는 중…'));
  const status = el('div', { class: 'form-status form-wide' });
  const button = el('button', { type: 'submit', class: 'button-primary' }, '기업 등록');
  const field = (label, name, props = {}) => el('label', { class: props.wide ? 'form-wide' : null }, label, el('input', { name, ...props, wide: null }));
  const form = el('form', { class: 'card org-form', onsubmit: onSubmit },
    el('h2', {}, '기업 등록'),
    el('p', { class: 'muted form-wide' }, '기업 정보와 그 기업의 관리자 계정을 만듭니다. 관리자 아이디와 초기 비밀번호를 기업에 전달하세요.'),
    el('div', { class: 'section-label' }, '기업 정보'),
    field('기업명 *', 'name', { required: true, maxlength: '80' }),
    field('사업자등록번호', 'businessNumber', { placeholder: '000-00-00000', maxlength: '20' }),
    field('대표자', 'representative', { maxlength: '40' }),
    field('주소', 'address', { maxlength: '120', wide: true }),
    el('div', { class: 'section-label' }, '담당자'),
    field('이름', 'contactName', { maxlength: '40' }),
    field('연락처', 'contactPhone', { type: 'tel', maxlength: '20' }),
    field('이메일', 'contactEmail', { type: 'email', maxlength: '120' }),
    el('div', { class: 'section-label' }, '기업 관리자 계정'),
    field('관리자 아이디 (이메일) *', 'adminEmail', { type: 'email', required: true, autocomplete: 'off', maxlength: '120' }),
    field('초기 비밀번호 (8자 이상) *', 'adminPassword', { type: 'password', required: true, minlength: '8', autocomplete: 'new-password' }),
    status,
    el('div', { class: 'form-wide' }, button),
  );
  const listStatus = el('div', { class: 'form-status' });
  content.replaceChildren(form, el('h2', {}, '등록된 기업'), listStatus, list);
  loadOrganizations();

  async function onSubmit(event) {
    event.preventDefault();
    const data = Object.fromEntries(new FormData(form).entries());
    button.disabled = true;
    status.replaceChildren();
    try {
      const result = await api.createOrganization(data);
      if (token !== renderToken) return;
      if (result.status === 'OK') {
        status.replaceChildren(message(`‘${data.name.trim()}’ 기업을 등록했어요. 관리자 아이디: ${result.admin_email}`, 'success'));
        form.reset();
        loadOrganizations();
      } else {
        status.replaceChildren(message(ORG_RESULT_MESSAGES[result.status] ?? result.status, 'error'));
      }
    } catch (error) {
      if (token === renderToken) status.replaceChildren(failure(error) ?? '');
    } finally {
      button.disabled = false;
    }
  }

  async function loadOrganizations() {
    try {
      const organizations = await api.allOrganizations();
      if (token !== renderToken) return;
      list.replaceChildren(organizations.length === 0 ? message('아직 등록된 기업이 없어요.') : el('div', { class: 'table-wrap' }, el('table', {},
        el('thead', {}, el('tr', {}, ['기업명', '사업자등록번호', '대표자', '담당자', '관리자 아이디', '차량', '등록일', '']
          .map((header) => el('th', { scope: 'col', class: header === '차량' ? 'num' : null }, header)))),
        el('tbody', {}, organizations.map(orgRow)),
      )));
    } catch (error) {
      if (token === renderToken) list.replaceChildren(failure(error) ?? '');
    }
  }

  function orgRow(org) {
    const actions = el('td', { class: 'actions' });
    const showDelete = () => actions.replaceChildren(el('button', { class: 'button-text danger', onclick: askDelete }, '삭제'));
    // 되돌릴 수 없어서 기업명을 한 번 더 입력받는다
    function askDelete() {
      const input = el('input', { 'aria-label': `${org.name} 삭제 확인`, placeholder: '기업명을 그대로 입력' });
      const confirmButton = el('button', { class: 'button-primary', onclick: () => remove(input.value, confirmButton) }, '영구 삭제');
      actions.replaceChildren(
        el('div', { class: 'delete-confirm' },
          el('span', { class: 'muted' }, '차량·운행 기록·직원 계정까지 모두 지워져요.'),
          input, confirmButton,
          el('button', { class: 'button-text', onclick: showDelete }, '취소'),
        ),
      );
      input.focus();
    }
    async function remove(name, confirmButton) {
      confirmButton.disabled = true;
      try {
        const result = await api.deleteOrganization(org.id, name.trim());
        if (result.status === 'NAME_MISMATCH') {
          listStatus.replaceChildren(message('기업명이 같지 않아요. 목록의 기업명을 그대로 입력하세요.', 'error'));
          confirmButton.disabled = false;
          return;
        }
        listStatus.replaceChildren(message(`‘${org.name}’ 기업을 삭제했어요 (차량 ${result.vehicles ?? 0}대, 계정 ${result.accounts ?? 0}개).`, 'success'));
        loadOrganizations();
      } catch (error) {
        listStatus.replaceChildren(failure(error) ?? '');
        confirmButton.disabled = false;
      }
    }
    showDelete();
    return el('tr', {},
      el('td', {}, org.name),
      el('td', {}, org.business_number ?? '-'),
      el('td', {}, org.representative ?? '-'),
      el('td', {}, [org.contact_name, org.contact_phone].filter(Boolean).join(' · ') || '-'),
      el('td', {}, org.admin_email ?? '-'),
      el('td', { class: 'num' }, String(org.vehicles?.[0]?.count ?? 0)),
      el('td', {}, formatDateTime(org.created_at).slice(0, 10)),
      actions,
    );
  }
}

// 열린 페이지에서 다른 QR 주소로 # 뒤만 바뀌면 새로 읽는다
window.addEventListener('hashchange', () => {
  if (parseShareHash(location.hash)) location.reload();
});

boot();

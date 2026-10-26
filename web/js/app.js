// 관리자 웹 화면: 로그인 → 운행기록부 / 차량 / 연결 코드
// 서버에서 온 글자는 항상 textContent로 넣는다 (innerHTML을 쓰지 않아 스크립트 삽입을 막는다).
import * as api from './api.js';
import { buildLogbook, csvFileName, km, kstParts, logbookCsv, monthRange, PURPOSE_LABELS } from './logbook.js';

const root = document.getElementById('app');

const TABS = [
  { id: 'logbook', label: '운행기록부' },
  { id: 'vehicles', label: '차량' },
  { id: 'pairing', label: '연결 코드' },
];

const state = {
  orgs: [],
  org: null,
  tab: 'logbook',
  month: currentMonth(),
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
  URL.revokeObjectURL(url);
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
  if (api.currentUser()) await enter();
  else renderLogin();
}

function renderLogin(notice) {
  const status = el('div', { class: 'form-status' }, notice ? message(notice, 'error') : null);
  const button = el('button', { type: 'submit', class: 'button-primary' }, '로그인');
  const form = el('form', { class: 'card login', onsubmit: onSubmit },
    el('h1', {}, '자동 운행기록부 관리자'),
    el('label', {}, '이메일', el('input', { name: 'email', type: 'email', autocomplete: 'username', required: true })),
    el('label', {}, '비밀번호',
      el('input', { name: 'password', type: 'password', autocomplete: 'current-password', required: true })),
    status,
    button,
  );
  setScreen(el('main', { class: 'center' }, form));

  async function onSubmit(event) {
    event.preventDefault();
    const data = new FormData(form);
    button.disabled = true;
    button.textContent = '로그인 중…';
    try {
      await api.signIn(String(data.get('email')).trim(), String(data.get('password')));
      await enter();
    } catch (error) {
      const text = error.code === 'invalid_credentials' ? '이메일 또는 비밀번호가 맞지 않아요.' : error.message;
      status.replaceChildren(message(text, 'error'));
      button.disabled = false;
      button.textContent = '로그인';
    }
  }
}

async function enter() {
  setScreen(el('main', { class: 'center' }, message('불러오는 중…')));
  try {
    state.orgs = await api.adminOrgs();
    if (state.orgs.length === 0) {
      setScreen(el('main', { class: 'center' },
        el('h1', {}, '관리자 권한이 없어요'),
        el('p', {}, `${api.currentUser().email} 계정은 관리자로 등록된 조직이 없어요.`),
        el('button', { class: 'button-secondary', onclick: logout }, '로그아웃'),
      ));
      return;
    }
    state.org = state.orgs.find((org) => org.id === state.org?.id) ?? state.orgs[0];
    await loadOrgData();
    renderMain();
  } catch (error) {
    const shown = failure(error);
    if (shown) setScreen(el('main', { class: 'center' }, shown, el('button', { class: 'button-secondary', onclick: logout }, '로그아웃')));
  }
}

async function loadOrgData() {
  [state.vehicles, state.drivers] = await Promise.all([api.vehicles(state.org.id), api.drivers(state.org.id)]);
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
      el('strong', { class: 'brand' }, '자동 운행기록부'),
      orgPicker,
      el('span', { class: 'spacer' }),
      el('span', { class: 'user' }, api.currentUser().email),
      el('button', { class: 'button-text', onclick: logout }, '로그아웃'),
    ),
    el('nav', { class: 'tabs', role: 'tablist' }, TABS.map((tab) => el('button', {
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
  ({ logbook: renderLogbook, vehicles: renderVehicles, pairing: renderPairing })[state.tab](content, renderToken);
}

// ─── 운행기록부 ────────────────────────────────────────────────────────

async function renderLogbook(content, token) {
  const { year, month } = state.month;
  const now = currentMonth();
  const isCurrentMonth = year === now.year && month === now.month;
  const moveMonth = (delta) => {
    const date = new Date(Date.UTC(year, month - 1 + delta, 1));
    state.month = { year: date.getUTCFullYear(), month: date.getUTCMonth() + 1 };
    renderMain();
  };
  const csvButton = el('button', { class: 'button-primary', disabled: true }, 'CSV 내려받기');
  const body = el('div', {}, message('불러오는 중…'));

  content.replaceChildren(
    el('div', { class: 'toolbar' },
      el('button', { class: 'button-secondary', onclick: () => moveMonth(-1), 'aria-label': '이전 달' }, '◀'),
      el('h2', { class: 'month' }, `${year}년 ${month}월`),
      el('button', {
        class: 'button-secondary', onclick: () => moveMonth(1), disabled: isCurrentMonth, 'aria-label': '다음 달',
      }, '▶'),
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
    const [from, to] = monthRange(year, month);
    const rows = await api.trips(state.org.id, from, to, state.vehicleId || undefined);
    if (token !== renderToken) return;
    const logbook = buildLogbook(rows, names());
    const selected = state.vehicles.find((vehicle) => vehicle.id === state.vehicleId);
    csvButton.disabled = logbook.rows.length === 0;
    csvButton.addEventListener('click', () => download(
      logbookCsv(logbook, { includeVehicle: !selected }),
      csvFileName(year, month, selected && vehicleName(selected)),
    ));
    body.replaceChildren(logbookSummary(logbook.summary), logbookTable(logbook, !selected));
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

function logbookTable(logbook, includeVehicle) {
  if (logbook.rows.length === 0) return message('이 달에 기록된 운행이 없어요.');
  const headers = ['사용일자', includeVehicle && '차량', '사용자', '시간', '주행 전(km)', '주행 후(km)', '주행거리(km)',
    '출퇴근용(km)', '일반업무용(km)', '비고(방문처)', '구분'].filter(Boolean);
  return el('div', { class: 'table-wrap' }, el('table', {},
    el('thead', {}, el('tr', {}, headers.map((header) => el('th', { scope: 'col', class: header.endsWith('(km)') ? 'num' : null }, header)))),
    el('tbody', {}, logbook.rows.map((row) => el('tr', {},
      el('td', {}, `${row.date}(${row.weekday})`),
      includeVehicle && el('td', {}, row.vehicle),
      el('td', {}, row.driver),
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

// ─── 차량 ─────────────────────────────────────────────────────────────

function renderVehicles(content, token) {
  if (state.vehicles.length === 0) {
    content.replaceChildren(message('아직 연결된 차량이 없어요. "연결 코드" 탭에서 코드를 발급해 차에서 입력하세요.'));
    return;
  }
  const status = el('div', { class: 'form-status' });
  content.replaceChildren(status, el('div', { class: 'table-wrap' }, el('table', {},
    el('thead', {}, el('tr', {}, ['차량 이름', 'VIN', '기본 운전자', '연결 상태', ''].map((header) => el('th', { scope: 'col' }, header)))),
    el('tbody', {}, state.vehicles.map((vehicle) => el('tr', {},
      el('td', { title: `차량 ID: ${vehicle.id}` }, vehicleName(vehicle)),
      el('td', {}, vehicle.vin ?? el('span', { class: 'muted' }, 'VIN 미확인 (기기 ID로 구분)')),
      el('td', {}, driverName(vehicle.default_driver_id)),
      el('td', {}, vehicle.device_user_id ? `연결됨 · ${formatDateTime(vehicle.paired_at)}` : el('span', { class: 'muted' }, '연결 해제됨')),
      el('td', { class: 'actions' }, vehicle.device_user_id && el('button', {
        class: 'button-text danger', onclick: () => unpair(vehicle),
      }, '연결 해제')),
    ))),
  )));

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

// ─── 연결 코드 ─────────────────────────────────────────────────────────

function renderPairing(content, token) {
  const result = el('div', { class: 'issued' });
  const list = el('div', {}, message('불러오는 중…'));
  const button = el('button', { type: 'submit', class: 'button-primary' }, '코드 발급');
  const form = el('form', { class: 'card pairing-form', onsubmit: onSubmit },
    el('h2', {}, '차량 연결 코드 발급'),
    el('p', { class: 'muted' }, '차가 정차한 상태에서 앱의 연결 화면에 입력하는 6자리 코드예요. 10분 동안 한 번만 쓸 수 있어요.'),
    el('label', {}, '차량 이름 (선택)', el('input', { name: 'vehicleName', maxlength: '40', placeholder: '예: 영업 1호차' })),
    button,
  );
  content.replaceChildren(el('div', { class: 'pairing' }, form, result), el('h2', {}, '사용할 수 있는 코드'), list);
  loadCodes();

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

boot();

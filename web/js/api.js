// Supabase REST API를 직접 부른다 (앱과 같은 방식, 라이브러리 없음).
// 웹에는 공개 키(Publishable key)만 들어간다. 데이터 보호는 서버의 RLS가 맡는다. secret/service_role 키는 절대 넣지 않는다.

const SESSION_KEY = 'drivelog.session';
// 만료 1분 전에 미리 토큰을 새로 받는다
const REFRESH_MARGIN_MS = 60 * 1000;

let config = { url: '', key: '' };
let session = loadSession();

export class ApiError extends Error {
  constructor(status, message, code) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export function configure({ url, key }) {
  config = { url: url.replace(/\/+$/, ''), key };
}

export const isConfigured = () => Boolean(config.url && config.key);

export const currentUser = () => session?.user ?? null;

function loadSession() {
  try {
    return JSON.parse(localStorage.getItem(SESSION_KEY));
  } catch {
    return null;
  }
}

function saveSession(value) {
  session = value;
  if (value) localStorage.setItem(SESSION_KEY, JSON.stringify(value));
  else localStorage.removeItem(SESSION_KEY);
}

function toSession(auth) {
  return {
    accessToken: auth.access_token,
    refreshToken: auth.refresh_token,
    expiresAtMs: Date.now() + auth.expires_in * 1000,
    user: { id: auth.user.id, email: auth.user.email, name: auth.user.user_metadata?.display_name ?? null },
  };
}

async function send(path, { method = 'GET', body, token, prefer } = {}) {
  const headers = { apikey: config.key, 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (prefer) headers.Prefer = prefer;
  let response;
  try {
    response = await fetch(config.url + path, { method, headers, body: body && JSON.stringify(body) });
  } catch {
    throw new ApiError(0, '서버에 연결할 수 없어요. 인터넷 연결을 확인해 주세요.');
  }
  const text = await response.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }
  if (!response.ok) {
    const message = data?.msg ?? data?.message ?? data?.error_description ?? String(text);
    throw new ApiError(response.status, message, data?.error_code ?? data?.code);
  }
  return data;
}

async function refresh() {
  try {
    saveSession(toSession(await send('/auth/v1/token?grant_type=refresh_token', {
      method: 'POST', body: { refresh_token: session.refreshToken },
    })));
  } catch (error) {
    saveSession(null);
    throw new ApiError(401, '로그인이 만료됐어요. 다시 로그인해 주세요.', error.code);
  }
}

async function accessToken() {
  if (!session) throw new ApiError(401, '로그인이 필요해요.');
  if (Date.now() > session.expiresAtMs - REFRESH_MARGIN_MS) await refresh();
  return session.accessToken;
}

/** 로그인한 관리자로 부른다. 토큰이 거절되면 한 번 새로 받아 다시 시도한다 */
async function authed(path, options = {}) {
  try {
    return await send(path, { ...options, token: await accessToken() });
  } catch (error) {
    if (error.status !== 401 || !session) throw error;
    await refresh();
    return send(path, { ...options, token: session.accessToken });
  }
}

const eq = (value) => `eq.${encodeURIComponent(value)}`;

// ─── 로그인 ───────────────────────────────────────────────────────────

// Supabase는 이메일로 로그인한다. 아이디(예: AltroStudio)는 실제로 존재할 수 없는 예약 도메인(.invalid)의 주소로 바꾼다
const ID_EMAIL_DOMAIN = 'drivelog.invalid';

/** 아이디 또는 이메일 → 로그인 이메일. 아이디는 대소문자를 구분하지 않는다 */
export function loginEmail(idOrEmail) {
  const value = idOrEmail.trim();
  return value.includes('@') ? value.toLowerCase() : `${value.toLowerCase()}@${ID_EMAIL_DOMAIN}`;
}

export async function signIn(idOrEmail, password) {
  saveSession(toSession(await send('/auth/v1/token?grant_type=password', {
    method: 'POST', body: { email: loginEmail(idOrEmail), password },
  })));
  return session.user;
}

/** 화면에 보일 이름: 계정에 적힌 표시 이름, 없으면 아이디(아이디로 만든 계정) 또는 이메일 */
export function displayName(user) {
  if (user.name) return user.name;
  return user.email?.endsWith(`@${ID_EMAIL_DOMAIN}`) ? user.email.slice(0, -ID_EMAIL_DOMAIN.length - 1) : user.email;
}

/** 이 브라우저에서만 로그아웃한다 (기본값 global은 같은 계정을 쓰는 다른 사람까지 로그아웃시킨다) */
export async function signOut() {
  const token = session?.accessToken;
  saveSession(null);
  if (token) await send('/auth/v1/logout?scope=local', { method: 'POST', token }).catch(() => {});
}

// ─── 조회 ────────────────────────────────────────────────────────────

/** 내가 관리자인 조직들 [{ id, name, businessNumber }] (사업자등록번호는 세무서 제출용 서식에 쓴다) */
export async function adminOrgs() {
  const rows = await authed(
    `/rest/v1/memberships?select=org_id,organizations(name,business_number)&role=eq.admin&user_id=${eq(currentUser().id)}`,
  );
  return rows.map((row) => ({
    id: row.org_id, name: row.organizations?.name ?? '조직', businessNumber: row.organizations?.business_number ?? null,
  }));
}

/** 내가 직원(driver)으로 승인된 조직들 [{ id, name, businessNumber }] */
export async function memberOrgs() {
  const rows = await authed(
    `/rest/v1/memberships?select=org_id,organizations(name,business_number)&role=eq.driver&user_id=${eq(currentUser().id)}`,
  );
  return rows.map((row) => ({
    id: row.org_id, name: row.organizations?.name ?? '조직', businessNumber: row.organizations?.business_number ?? null,
  }));
}

/** 내 직원 가입 신청 (없으면 null) → { status: 'PENDING' | 'APPROVED' | 'REJECTED', created_at } */
export async function myMemberRequest() {
  const [row] = await authed(`/rest/v1/member_requests?select=status,created_at&user_id=${eq(currentUser().id)}`);
  return row ?? null;
}

export const vehicles = (orgId) => authed(
  `/rest/v1/vehicles?select=id,vin,display_name,model,plate_number,default_driver_id,device_user_id,paired_at,created_at`
  + `&org_id=${eq(orgId)}&order=created_at`,
);

/** 운전자 명단 (0010: 부서·숨김·맞춤 설정). 직원 계정이 있는 운전자는 user_id가 있다 */
export const drivers = (orgId) => authed(
  `/rest/v1/drivers?select=id,name,department,archived,comfort,comfort_updated_at,user_id&org_id=${eq(orgId)}&order=created_at`,
);

/** 관리자가 계정 없는 운전자를 추가한다 (차의 운전자 명단에 10분 안에 나타난다) */
export const addDriver = (orgId, name, department) => authed('/rest/v1/drivers', {
  method: 'POST',
  body: { org_id: orgId, name, department: department || null },
});

/** 운전자를 숨긴다(퇴사·휴직). 기록은 그대로 두고 차의 명단에서만 뺀다 */
export const setDriverArchived = (driverId, archived) => authed(`/rest/v1/drivers?id=${eq(driverId)}`, {
  method: 'PATCH',
  body: { archived },
});

/** 차량마다 운행을 마칠 때 보고한 마지막 상태 (0010) */
export const vehicleStatus = (orgId) => authed(
  `/rest/v1/vehicle_status?select=vehicle_id,reported_at_ms,driver_name,odometer_km,soc_percent,open_windows,doors_locked,cabin_light_on,tidied`
  + `&org_id=${eq(orgId)}`,
);

// 서버(Supabase)는 한 번에 최대 1000줄만 준다. 그보다 많으면 나눠 받는다 (2026-10-27 점검: 1년치 세무 서식이 잘릴 수 있었다)
const PAGE_SIZE = 500;

/** fetchPage(offset, limit)을 줄이 모자랄 때까지 불러 이어 붙인다 */
export async function fetchAllPages(fetchPage, pageSize = PAGE_SIZE) {
  const rows = [];
  for (let offset = 0; ; offset += pageSize) {
    const page = await fetchPage(offset, pageSize);
    rows.push(...page);
    if (page.length < pageSize) return rows;
  }
}

/** 그 기간 [fromMs, toMs)에 시작한 운행. vehicleId가 없으면 조직의 모든 차량 */
export function trips(orgId, fromMs, toMs, vehicleId) {
  const vehicleFilter = vehicleId ? `&vehicle_id=${eq(vehicleId)}` : '';
  return fetchAllPages((offset, limit) => authed(
    '/rest/v1/trips?select=id,vehicle_id,driver_id,driver_name,driver_department,started_at_ms,ended_at_ms,start_odometer_km,end_odometer_km,visit_note,work_note,purpose'
    + `&org_id=${eq(orgId)}&started_at_ms=gte.${fromMs}&started_at_ms=lt.${toMs}${vehicleFilter}`
    + `&order=started_at_ms,id&limit=${limit}&offset=${offset}`,
  ));
}

/** 아직 쓸 수 있는 연결 코드 (만료 전, 사용 전 또는 여러 번 쓰는 데모 코드) */
export const activePairingCodes = (orgId) => authed(
  `/rest/v1/pairing_codes?select=code,vehicle_name,expires_at,reusable,used_at&org_id=${eq(orgId)}`
  + `&expires_at=gt.${encodeURIComponent(new Date().toISOString())}&or=(used_at.is.null,reusable.is.true)&order=expires_at`,
);

// ─── 개발자(서비스 운영) ──────────────────────────────────────────────

/** 개발자 계정인지 (개발자는 기업을 등록한다) */
export async function isDeveloper() {
  const rows = await authed(`/rest/v1/developers?select=user_id&user_id=${eq(currentUser().id)}`);
  return rows.length > 0;
}

/** 등록된 모든 기업과 차량 수 (개발자만. 운행 기록은 볼 수 없다) */
export const allOrganizations = () => authed(
  '/rest/v1/organizations?select=id,name,business_number,representative,address,contact_name,contact_phone,contact_email,'
  + 'admin_email,created_at,vehicles(count)&order=created_at.desc',
);

/** 기업을 등록하고 기업 관리자 계정을 만든다 → { status, org_id?, admin_email? } */
export const createOrganization = (form) => authed('/rest/v1/rpc/create_organization', {
  method: 'POST',
  body: {
    p_name: form.name,
    p_business_number: form.businessNumber,
    p_representative: form.representative,
    p_address: form.address,
    p_contact_name: form.contactName,
    p_contact_phone: form.contactPhone,
    p_contact_email: form.contactEmail,
    p_admin_email: form.adminEmail,
    p_admin_password: form.adminPassword,
  },
});

/**
 * 휴대폰으로 받기: 차 화면의 QR 코드 링크로 운행기록부를 읽는다 (로그인 없이, 10분 동안).
 * → { status: 'OK' | 'EXPIRED', org, vehicle, drivers, trips, from_ms, to_ms, expires_at }
 */
export const sharedLogbook = (token) => send('/rest/v1/rpc/shared_logbook', { method: 'POST', body: { p_token: token } });

// ─── 직원 가입 (관리자) ─────────────────────────────────────────────────

/** 기업 가입 코드 (관리자가 직원에게 알려 준다) */
export async function joinCode(orgId) {
  const [row] = await authed(`/rest/v1/organizations?select=join_code&id=${eq(orgId)}`);
  return row?.join_code ?? null;
}

/** 직원 가입 신청 목록 (최근 것부터) */
export const memberRequests = (orgId) => authed(
  `/rest/v1/member_requests?select=id,user_id,name,department,employee_no,phone,status,created_at&org_id=${eq(orgId)}&order=created_at.desc`,
);

/** 직원별 배정 차량 [{ vehicle_id, user_id }] */
export const vehicleMembers = (orgId) => authed(`/rest/v1/vehicle_members?select=vehicle_id,user_id&org_id=${eq(orgId)}`);

/**
 * 직원 가입 신청 (로그인 없이). 기업 가입 코드와 소속 확인 정보를 낸다. 관리자가 승인해야 기록을 볼 수 있다.
 * → { status: 'OK' | 'INVALID_CODE' | 'INVALID_NAME' | 'INVALID_DEPARTMENT' | 'INVALID_PHONE' | 'INVALID_EMAIL' | 'WEAK_PASSWORD' | 'EMAIL_TAKEN' | 'TOO_MANY', org_name }
 */
export const memberSignUp = (form) => send('/rest/v1/rpc/member_sign_up', {
  method: 'POST',
  body: {
    p_join_code: form.joinCode, p_email: loginEmail(form.email), p_password: form.password, p_name: form.name,
    p_department: form.department, p_employee_no: form.employeeNo || null, p_phone: form.phone,
  },
});

// ─── 변경 ────────────────────────────────────────────────────────────

/** 6자리 연결 코드를 발급한다 (10분 유효, 한 번만 사용) → { code, expires_at } */
export async function issuePairingCode(orgId, vehicleName) {
  const [issued] = await authed('/rest/v1/rpc/issue_pairing_code', {
    method: 'POST', body: { p_org: orgId, p_vehicle_name: vehicleName || null },
  });
  return issued;
}

/** 차량의 차종·자동차등록번호를 적는다 (세무서 제출용 서식의 ①②). 바뀐 줄이 없으면 권한이 없는 것이다 */
export async function updateVehicleRegistration(vehicleId, { model, plateNumber }) {
  const rows = await authed(`/rest/v1/vehicles?id=${eq(vehicleId)}&select=id`, {
    method: 'PATCH', body: { model: model || null, plate_number: plateNumber || null }, prefer: 'return=representation',
  });
  if (rows.length === 0) throw new ApiError(403, '이 차량을 고칠 권한이 없어요.');
}

/** 직원 가입 신청을 승인(쓰는 차량과 함께)하거나 거절한다 */
export const decideMemberRequest = (requestId, approve, vehicleIds = []) => authed('/rest/v1/rpc/decide_member_request', {
  method: 'POST', body: { p_request: requestId, p_approve: approve, p_vehicle_ids: vehicleIds },
});

/** 직원이 쓰는 차량을 바꾼다 */
export const setMemberVehicles = (userId, orgId, vehicleIds) => authed('/rest/v1/rpc/set_member_vehicles', {
  method: 'POST', body: { p_user: userId, p_org: orgId, p_vehicle_ids: vehicleIds },
});

/** 직원을 내보낸다 (퇴사 등). 그 뒤로는 기록을 볼 수 없다 */
export const removeMember = (userId, orgId) => authed('/rest/v1/rpc/remove_member', {
  method: 'POST', body: { p_user: userId, p_org: orgId },
});

/** 기업 가입 코드를 새로 만든다 (이미 가입한 직원은 그대로) */
export const regenerateJoinCode = (orgId) => authed('/rest/v1/rpc/regenerate_join_code', {
  method: 'POST', body: { p_org: orgId },
});

/** 개발자: 기업과 그 차량·운행·계정을 지운다. 기업명을 한 번 더 받아 실수를 막는다 → { status: 'OK' | 'NAME_MISMATCH' | 'NOT_FOUND' } */
export const deleteOrganization = (orgId, confirmName) => authed('/rest/v1/rpc/delete_organization', {
  method: 'POST', body: { p_org: orgId, p_confirm_name: confirmName },
});

/** 차량 연결을 해제한다. 운행 기록은 그대로 남는다 */
export const unpairVehicle = (vehicleId) => authed('/rest/v1/rpc/unpair_vehicle', {
  method: 'POST', body: { p_vehicle: vehicleId },
});

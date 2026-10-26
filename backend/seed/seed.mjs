// 시연용 데모 조직, 관리자 계정, 연결 코드를 만든다. 여러 번 실행해도 같은 결과가 된다.
// 데모 데이터는 이 스크립트에만 있고 APK에는 넣지 않는다.
// 사용: node backend/seed/seed.mjs
import { loadEnv } from '../scripts/env.mjs';

const env = loadEnv([
  'SUPABASE_URL', 'SUPABASE_SERVICE_ROLE_KEY',
  'DEMO_ORG_NAME', 'DEMO_ADMIN_EMAIL', 'DEMO_ADMIN_PASSWORD', 'DEMO_PAIRING_CODE',
]);
if (!/^[0-9]{6}$/.test(env.DEMO_PAIRING_CODE)) throw new Error('DEMO_PAIRING_CODE 는 6자리 숫자여야 합니다.');

const DEMO_CODE_EXPIRES_AT = '2027-02-28T23:59:59+09:00';

// 예전 service_role 키(JWT)는 Authorization에도 넣고, 새 secret 키(sb_secret_...)는 apikey에만 넣는다
const isJwtKey = env.SUPABASE_SERVICE_ROLE_KEY.startsWith('eyJ');

async function api(path, { method = 'GET', body, prefer } = {}) {
  const response = await fetch(env.SUPABASE_URL + path, {
    method,
    headers: {
      apikey: env.SUPABASE_SERVICE_ROLE_KEY,
      ...(isJwtKey && { Authorization: `Bearer ${env.SUPABASE_SERVICE_ROLE_KEY}` }),
      'Content-Type': 'application/json',
      ...(prefer && { Prefer: prefer }),
    },
    body: body && JSON.stringify(body),
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`${method} ${path} → ${response.status} ${text}`);
  return text ? JSON.parse(text) : null;
}

// 1. 관리자 계정. 이메일 확인 처리해서 만들므로 실제 메일은 발송되지 않는다
async function ensureAdmin() {
  const { users } = await api('/auth/v1/admin/users?per_page=1000');
  const existing = users.find((user) => user.email === env.DEMO_ADMIN_EMAIL);
  if (existing) {
    await api(`/auth/v1/admin/users/${existing.id}`, { method: 'PUT', body: { password: env.DEMO_ADMIN_PASSWORD } });
    return existing;
  }
  return api('/auth/v1/admin/users', {
    method: 'POST',
    body: { email: env.DEMO_ADMIN_EMAIL, password: env.DEMO_ADMIN_PASSWORD, email_confirm: true },
  });
}

// 2. 데모 조직 (이름으로 찾고 없으면 만든다)
async function ensureOrg() {
  const [existing] = await api(`/rest/v1/organizations?name=eq.${encodeURIComponent(env.DEMO_ORG_NAME)}&select=*`);
  if (existing) return existing;
  const [created] = await api('/rest/v1/organizations', {
    method: 'POST', body: { name: env.DEMO_ORG_NAME }, prefer: 'return=representation',
  });
  return created;
}

const admin = await ensureAdmin();
const org = await ensureOrg();

await api('/rest/v1/memberships?on_conflict=user_id,org_id', {
  method: 'POST',
  body: { user_id: admin.id, org_id: org.id, role: 'admin', display_name: '데모 관리자' },
  prefer: 'resolution=merge-duplicates',
});

// 3. 시연용 연결 코드 (여러 번 사용 가능)
await api('/rest/v1/pairing_codes?on_conflict=code', {
  method: 'POST',
  body: {
    code: env.DEMO_PAIRING_CODE, org_id: org.id, vehicle_name: '데모 차량',
    expires_at: DEMO_CODE_EXPIRES_AT, reusable: true, used_at: null,
  },
  prefer: 'resolution=merge-duplicates',
});

console.log(`데모 조직: ${org.name}`);
console.log(`관리자 계정: ${env.DEMO_ADMIN_EMAIL} (비밀번호는 backend/.env)`);
console.log(`연결 코드: backend/.env 의 DEMO_PAIRING_CODE (${DEMO_CODE_EXPIRES_AT}까지, 여러 번 사용 가능)`);

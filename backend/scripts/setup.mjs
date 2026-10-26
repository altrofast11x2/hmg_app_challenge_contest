// Supabase 프로젝트 준비: 마이그레이션 SQL 적용 + 익명 로그인(차량 계정용) 켜기.
// Supabase Management API를 쓰므로 backend/.env 의 SUPABASE_ACCESS_TOKEN 이 필요하다.
// 사용: node backend/scripts/setup.mjs
import { readdirSync, readFileSync } from 'node:fs';
import { loadEnv, projectRef } from './env.mjs';

const env = loadEnv(['SUPABASE_URL', 'SUPABASE_ACCESS_TOKEN']);
const ref = projectRef(env.SUPABASE_URL);
const MIGRATIONS_DIR = new URL('../supabase/migrations/', import.meta.url);

async function management(path, { method = 'GET', body } = {}) {
  const response = await fetch(`https://api.supabase.com/v1/projects/${ref}${path}`, {
    method,
    headers: { Authorization: `Bearer ${env.SUPABASE_ACCESS_TOKEN}`, 'Content-Type': 'application/json' },
    body: body && JSON.stringify(body),
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`${method} ${path} → ${response.status} ${text}`);
  return text ? JSON.parse(text) : null;
}

const query = (sql) => management('/database/query', { method: 'POST', body: { query: sql } });

// 1. 마이그레이션: 적용한 파일 이름을 기록해 두고 새 파일만 적용한다
await query('create table if not exists public.drivelog_migrations (name text primary key, applied_at timestamptz not null default now())');
await query('alter table public.drivelog_migrations enable row level security');
const applied = new Set((await query('select name from public.drivelog_migrations')).map((row) => row.name));
const files = readdirSync(MIGRATIONS_DIR).filter((name) => name.endsWith('.sql')).sort();
for (const name of files) {
  if (applied.has(name)) {
    console.log(`건너뜀 (이미 적용): ${name}`);
    continue;
  }
  await query(readFileSync(new URL(name, MIGRATIONS_DIR), 'utf8'));
  await query(`insert into public.drivelog_migrations (name) values ('${name.replaceAll("'", "''")}')`);
  console.log(`적용: ${name}`);
}

// 2. 차량 앱은 비밀번호 없이 익명 로그인으로 기기 계정을 만든다
await management('/config/auth', { method: 'PATCH', body: { external_anonymous_users_enabled: true } });
console.log('익명 로그인 켬');
console.log('완료. 다음: node backend/seed/seed.mjs');

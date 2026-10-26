// backend/.env 를 읽는다. 값은 화면에 출력하지 않는다
import { existsSync, readFileSync } from 'node:fs';

const ENV_FILE = new URL('../.env', import.meta.url);

export function loadEnv(required) {
  if (!existsSync(ENV_FILE)) {
    throw new Error('backend/.env 가 없습니다. backend/.env.example 을 복사해서 채워 주세요.');
  }
  const env = {};
  for (const line of readFileSync(ENV_FILE, 'utf8').split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#') || !trimmed.includes('=')) continue;
    const index = trimmed.indexOf('=');
    env[trimmed.slice(0, index).trim()] = trimmed.slice(index + 1).trim();
  }
  const missing = required.filter((key) => !env[key]);
  if (missing.length > 0) {
    throw new Error(`backend/.env 에 값이 비어 있습니다: ${missing.join(', ')}`);
  }
  return env;
}

export function projectRef(supabaseUrl) {
  return new URL(supabaseUrl).hostname.split('.')[0];
}

// Supabase 주소·공개 키를 읽는다: 환경 변수 SUPABASE_URL·SUPABASE_PUBLISHABLE_KEY (배포용), 없으면 저장소 루트 local.properties
import { existsSync, readFileSync } from 'node:fs';

const LOCAL_PROPERTIES = new URL('../../local.properties', import.meta.url);

function readLocalProperties() {
  if (!existsSync(LOCAL_PROPERTIES)) return {};
  return Object.fromEntries(readFileSync(LOCAL_PROPERTIES, 'utf8').split(/\r?\n/)
    .filter((line) => line.includes('=') && !line.trimStart().startsWith('#'))
    .map((line) => [line.slice(0, line.indexOf('=')).trim(), line.slice(line.indexOf('=') + 1).trim()]));
}

/** 웹에 넣을 config.js 내용. 값이 없거나 관리용 키면 멈춘다 */
export function configSource() {
  const props = readLocalProperties();
  const url = process.env.SUPABASE_URL || props['supabase.url'] || '';
  const key = process.env.SUPABASE_PUBLISHABLE_KEY || props['supabase.anonKey'] || '';

  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/.test(url) || !key) {
    console.error('Supabase 주소나 공개 키가 없습니다. local.properties의 supabase.url·supabase.anonKey를 채우세요.');
    process.exit(1);
  }
  // 관리용 키가 웹에 들어가는 사고를 막는다
  const jwtRole = key.startsWith('eyJ') ? JSON.parse(Buffer.from(key.split('.')[1], 'base64url').toString()).role : null;
  if (key.startsWith('sb_secret_') || jwtRole === 'service_role') {
    console.error('secret/service_role 키는 웹에 넣을 수 없습니다. 공개 키(Publishable key)를 쓰세요.');
    process.exit(1);
  }
  return '// web/scripts가 만든 파일. 커밋하지 않는다\n'
    + `export const SUPABASE_URL = ${JSON.stringify(url)};\n`
    + `export const SUPABASE_KEY = ${JSON.stringify(key)};\n`;
}

// 배포용 폴더 web/dist를 만든다. 공개할 파일(화면, 스타일, 스크립트, vercel.json)만 담고 scripts·test·README는 넣지 않는다.
// 사용: node web/scripts/build.mjs  →  vercel deploy web/dist --prod
import { cpSync, existsSync, mkdirSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { configSource } from './config.mjs';

const WEB = new URL('../', import.meta.url);
const DIST = new URL('dist/', WEB);
const PUBLIC_FILES = ['index.html', 'vercel.json', 'css/tokens.css', 'css/app.css', 'js/app.js', 'js/api.js', 'js/logbook.js'];

const source = configSource();
// 이전 결과를 지우되, vercel CLI가 프로젝트 연결 정보를 두는 .vercel 폴더는 남긴다
if (existsSync(DIST)) {
  for (const entry of readdirSync(DIST)) {
    if (entry !== '.vercel') rmSync(new URL(entry, DIST), { recursive: true, force: true });
  }
}
for (const file of PUBLIC_FILES) {
  mkdirSync(new URL('.', new URL(file, DIST)), { recursive: true });
  cpSync(new URL(file, WEB), new URL(file, DIST));
}
writeFileSync(new URL('js/config.js', DIST), source);
console.log(`web/dist를 만들었습니다 (${PUBLIC_FILES.length + 1}개 파일).`);

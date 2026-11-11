// 배포용 폴더 web/dist를 만든다. 공개할 파일(화면, 스타일, 스크립트, vercel.json)만 담고 scripts·test·README는 넣지 않는다.
// 사용: node web/scripts/build.mjs  →  vercel deploy web/dist --prod
import { cpSync, existsSync, mkdirSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { configSource } from './config.mjs';
import { privacyPage } from './privacy.mjs';

const WEB = new URL('../', import.meta.url);
const DIST = new URL('dist/', WEB);
// app-version.json: 차량 앱이 하루 한 번 받아 보는 최신 버전 (앱 versionCode를 올려 배포할 때 같이 올린다)
// index.html = 제품 소개 페이지, app.html = 관리자 웹 (예전 QR 주소 /#share=는 소개 페이지가 app.html로 넘긴다)
const PUBLIC_FILES = [
  'index.html', 'app.html', 'vercel.json', 'app-version.json',
  'css/tokens.css', 'css/app.css', 'css/policy.css', 'css/landing.css',
  'js/app.js', 'js/api.js', 'js/logbook.js', 'js/signup.js', 'js/landing.js',
  'img/hero-cockpit.webp', 'img/screen-driving.webp', 'img/screen-classify.webp', 'img/screen-home.webp', 'img/screen-picker.webp',
];

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
// 소스 코드·APK 묶음 (공모전 제출용 내려받기 주소: /download/AltroStudio_Chagok_source_apk.zip).
// tools/submission-copy.sh가 web/download/에 만든다 (커밋하지 않는다). 없으면 넘어간다
const DOWNLOAD = new URL('download/', WEB);
let downloads = 0;
if (existsSync(DOWNLOAD)) {
  mkdirSync(new URL('download/', DIST), { recursive: true });
  for (const entry of readdirSync(DOWNLOAD)) {
    cpSync(new URL(entry, DOWNLOAD), new URL(`download/${entry}`, DIST));
    downloads += 1;
  }
}
// 공개 개인정보 처리방침 (App Market 제출용 URL: /privacy.html)
writeFileSync(new URL('privacy.html', DIST), privacyPage());
console.log(`web/dist를 만들었습니다 (${PUBLIC_FILES.length + 2}개 파일, 내려받기 ${downloads}개).`);

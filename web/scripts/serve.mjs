// 개발용 정적 파일 서버 (의존성 없음). 이 PC에서만 열린다.
// 사용: node web/scripts/serve.mjs  → http://127.0.0.1:5173
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = fileURLToPath(new URL('../', import.meta.url));
const PORT = Number(process.env.PORT) || 5173;
const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.webp': 'image/webp',
  '.png': 'image/png',
  '.json': 'application/json; charset=utf-8',
};

createServer(async (request, response) => {
  const path = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
  const file = normalize(join(ROOT, path.endsWith('/') ? `${path}index.html` : path));
  // web/ 밖의 파일과 scripts·test 폴더는 내보내지 않는다
  const inside = file.startsWith(ROOT) && !file.slice(ROOT.length).split(sep).some((part) => ['scripts', 'test', '..'].includes(part));
  if (request.method !== 'GET' || !inside || !TYPES[extname(file)]) {
    response.writeHead(404).end('Not found');
    return;
  }
  try {
    const body = await readFile(file);
    response.writeHead(200, { 'Content-Type': TYPES[extname(file)], 'Cache-Control': 'no-store' }).end(body);
  } catch {
    response.writeHead(404).end('Not found');
  }
}).listen(PORT, '127.0.0.1', () => console.log(`소개 페이지: http://127.0.0.1:${PORT}/ · 관리자 웹: http://127.0.0.1:${PORT}/app.html`));

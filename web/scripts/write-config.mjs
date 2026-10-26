// web/js/config.js를 만든다 (커밋하지 않는 파일, 개발용 서버에서 쓴다)
// 사용: node web/scripts/write-config.mjs
import { writeFileSync } from 'node:fs';
import { configSource } from './config.mjs';

writeFileSync(new URL('../js/config.js', import.meta.url), configSource());
console.log('web/js/config.js를 만들었습니다.');

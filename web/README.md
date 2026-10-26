# web — 관리자 웹 최소판

기업 담당자가 차량을 연결하고 운행기록부를 보는 웹입니다. 순수 HTML·CSS·JavaScript(ES 모듈)이고 설치할 패키지와 빌드 단계가 없습니다. 서버는 차량 앱과 같은 Supabase REST API를 직접 부릅니다.

| 화면 | 할 수 있는 것 |
|---|---|
| 로그인 | 관리자 이메일·비밀번호 로그인 (관리자로 등록된 조직이 없으면 안내) |
| 운행기록부 | 월 이동, 차량 고르기(전체/한 대), 월 요약(총 주행거리·업무용 사용거리·업무사용비율·미분류), 국세청 서식 열의 표, CSV 내려받기 |
| 차량 | **연결 코드 발급**(6자리, 10분 유효, 1회용, 남은 시간 표시), 지금 쓸 수 있는 코드 목록(시연용 코드 포함), 연결된 차량 목록(이름, VIN 또는 "VIN 미확인", 기본 운전자, 연결 상태), 연결 해제 |

연결 코드 발급을 차량 탭에 두는 이유: 앱 연결 화면이 "관리자 웹 › 차량 › 연결 코드 발급"이라고 안내한다 (`design/specs/A02-connect.md`).

## 실행

```bash
node web/scripts/write-config.mjs   # web/js/config.js 생성 (커밋 금지)
node web/scripts/serve.mjs          # http://127.0.0.1:5173
cd web && node --test               # 운행기록부·CSV 단위 테스트
```

- `write-config.mjs`는 환경 변수 `SUPABASE_URL`·`SUPABASE_PUBLISHABLE_KEY`를 먼저 보고, 없으면 루트 `local.properties`의 `supabase.url`·`supabase.anonKey`를 읽습니다. secret/service_role 키를 넣으면 거부합니다.
- `serve.mjs`는 개발용 서버입니다. 이 PC(127.0.0.1)에서만 열리고 `web/` 밖의 파일과 `scripts/`·`test/`는 내보내지 않습니다.

## 배포 (Vercel)

빌드 단계가 없는 정적 사이트라, 공개할 파일만 모은 `web/dist`를 그대로 올린다 (`scripts/`·`test/`·README는 올라가지 않는다).

```bash
node web/scripts/build.mjs                       # web/dist 생성 (config.js 포함, 커밋 금지)
vercel login                                     # 처음 한 번 (사용자가 직접)
vercel link --cwd web/dist --project altrostudio-drivelog --scope altrofast11x2s-projects --yes   # 처음 한 번
vercel deploy --cwd web/dist --prod --yes
```

- `vercel.json`이 보안 헤더(다른 사이트가 이 화면을 틀 안에 넣지 못하게 막기 등)를 붙인다.
- 2026-10-26 Vercel MCP 연결로는 프로젝트 생성이 거부돼서(403) CLI로 배포한다.

## 구조

| 파일 | 내용 |
|---|---|
| `index.html` | 진입점. Content-Security-Policy로 이 사이트의 파일과 Supabase API만 허용 |
| `js/api.js` | 로그인·토큰 갱신·조회·코드 발급·연결 해제 (Supabase REST) |
| `js/logbook.js` | 월별 운행기록부 계산과 CSV. 앱의 `LogbookBuilder`·`LogbookCsv`와 같은 규칙 (DOM을 쓰지 않아 Node에서 시험) |
| `js/app.js` | 화면. 서버에서 온 글자는 `textContent`로만 넣는다 |
| `css/app.css` | 임시 스타일. 디자인은 기능을 모두 만든 뒤 `design/tokens`의 토큰으로 바꾼다 |

## 보안

- 웹에는 공개 키만 들어갑니다. 무엇을 볼 수 있고 바꿀 수 있는지는 서버의 RLS가 정합니다 (`backend/README.md`).
- 관리자는 운행 기록을 고칠 수 없고, 차량을 지울 수도 없습니다(연결 해제만 가능).
- 로그인 정보는 브라우저 `localStorage`에 둡니다. 로그아웃은 이 브라우저에서만 합니다(같은 계정을 쓰는 다른 사람은 로그아웃되지 않음).

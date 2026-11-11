# web: 소개 페이지와 관리자 웹

순수 HTML·CSS·JavaScript(ES 모듈)이고 설치할 패키지와 빌드 단계가 없습니다. 서버는 차량 앱과 같은 Supabase REST API를 직접 부릅니다.

| 주소 | 내용 |
|---|---|
| `/` (`index.html`) | 제품 소개 페이지: 문제, 운행 한 번의 흐름(실제 에뮬레이터 화면), 차량 연동과 쓰는 Pleos SDK API, 기업용 기능, 신뢰·개인정보, 도입. 외부 스크립트 없음 (`css/landing.css`, `img/`) |
| `/app.html` | 관리자 웹 (아래 표). 예전 앱(0.2.0)의 휴대폰 QR 주소 `/#share=…`는 소개 페이지가 `app.html#share=…`로 넘긴다 (`js/landing.js`) |
| `/privacy.html` | 개인정보 처리방침 (빌드할 때 앱 문구로 만든다) |

| 화면 | 할 수 있는 것 |
|---|---|
| 현황 (0010) | 이번 달 요약, 차량별 반납 상태(운행을 마칠 때 차가 보고: 마지막 운전자·반납 시각, 배터리, 열린 창문, 문 잠김, 실내등, 계기판, 운전자가 차 화면에서 정리한 항목, 점검 필요 표시), 최근 업무 일지 |
| 업무 일지 (0010) | 일반업무 운행만 날짜별로: 시간, 운전자·부서, 차량, 방문처, 업무 내용(운전자가 정차 후 차에 말하거나 Gleo AI에 말한 내용), 거리. 달·차량 고르기, CSV |
| 운전자 (0010, 기업 관리자) | 계정 없는 운전자 추가(이름·부서), 숨기기·다시 보이기, 운전자별 맞춤 설정(차가 기억한 온도·열선·무드등 색). 승인된 직원은 차가 명단을 받을 때 자동으로 운전자가 된다 |
| 로그인 | 관리자 아이디(예: AltroStudio) 또는 이메일 + 비밀번호. 아이디는 웹이 내부 주소 `<아이디>@drivelog.invalid`로 바꿔 로그인한다 (관리자로 등록된 조직이 없으면 안내) |
| 운행기록부 | 월 이동·달 바로 고르기, 날짜로 기간 고르기(시작~끝, 366일까지), 차량 고르기(전체/한 대), 기간 요약(총 주행거리·업무용 사용거리·업무사용비율·미분류), 국세청 서식 열의 표(사용자 = 운행 당시 운전자 이름·부서, 비고 = 방문처 · 업무 내용), CSV 내려받기 |
| 기업 등록 (개발자 계정) | 기업 정보(기업명·사업자등록번호·대표자·주소·담당자)와 기업 관리자 계정(아이디·초기 비밀번호)을 만들어 등록, 등록된 기업 목록과 차량 수 |
| 직원 회원가입 (로그인 화면) | 기업 가입 코드 + 이름·부서·사번·휴대폰 + 아이디(이메일)·비밀번호 + 개인정보 동의. 신청하면 "승인 대기", 관리자가 승인하면 배정받은 차량의 운행기록부·세무 제출만 보인다 (`js/signup.js`가 입력 확인) |
| 직원 (기업 관리자) | 기업 가입 코드 보기·새로 만들기, 가입 신청(이름·부서·사번·휴대폰)을 쓰는 차량과 함께 승인·거절, 직원별 차량 바꾸기, 내보내기 |
| 기업 삭제 (개발자 계정) | 등록된 기업 목록의 [삭제] → 기업명을 그대로 입력해야 삭제. 차량·운행·직원 계정까지 지운다 |
| 휴대폰으로 받기 (`/app.html#share=토큰`, 예전 `/#share=`도 넘겨 줌) | 차 화면의 QR 코드로 연다. 로그인 없이 그 차량의 그 기간 운행기록부(요약·표), CSV 저장, 휴대폰 공유(카톡·메일, 브라우저가 파일 공유를 지원할 때), 세무서 제출용 서식·PDF. 링크는 10분 동안만 열리고, 만료되면 안내만 보여 준다 |
| 세무 제출 | 사업연도(기본: 올해 1월 1일~12월 31일) 고르기, 차량별 총주행 거리·업무용 사용거리·업무사용비율 표와 CSV(명세서용), 국세청고시 제2025-8호 별지 「업무용승용차 운행기록부」와 같은 칸의 서식 미리보기와 인쇄(A4 가로, PDF로 저장) |
| 차량 | 차종·자동차등록번호 적기(서식의 ①②), **연결 코드 발급**(6자리, 10분 유효, 1회용, 남은 시간 표시), 지금 쓸 수 있는 코드 목록(시연용 코드 포함), 연결된 차량 목록(이름, VIN 또는 "VIN 미확인", 기본 운전자, 연결 상태), 연결 해제 |

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

배포 주소: **https://altrostudio-drivelog.vercel.app** (프로젝트 `altrofast11x2s-projects/altrostudio-drivelog`, 2026-10-26 첫 배포)

빌드 단계가 없는 정적 사이트라, 공개할 파일만 모은 `web/dist`를 그대로 올린다 (`scripts/`·`test/`·README는 올라가지 않는다).

```bash
node web/scripts/build.mjs                       # web/dist 생성 (config.js 포함, 커밋 금지). web/download/에 ZIP이 있으면 함께 담는다
vercel login                                     # 처음 한 번 (사용자가 직접)
vercel link --cwd web/dist --project altrostudio-drivelog --scope altrofast11x2s-projects --yes   # 처음 한 번
vercel deploy --cwd web/dist --prod --yes
```

- 소개 페이지의 [소스 코드·APK 내려받기]는 `/download/AltroStudio_Chagok_source_apk.zip`을 받는다 (공모전 제출용 내려받기 주소). 이 파일은 저장소에 넣지 않고 배포할 때만 만든다.
- `vercel.json`이 보안 헤더(다른 사이트가 이 화면을 틀 안에 넣지 못하게 막기 등)를 붙인다.
- 2026-10-26 Vercel MCP 연결로는 프로젝트 생성이 거부돼서(403) CLI로 배포한다.

## 구조

| 파일 | 내용 |
|---|---|
| `index.html` | 진입점. Content-Security-Policy로 이 사이트의 파일과 Supabase API만 허용 |
| `js/api.js` | 로그인·토큰 갱신·조회·코드 발급·연결 해제 (Supabase REST) |
| `js/logbook.js` | 운행기록부 계산(달·날짜 기간)과 CSV. 앱의 `LogbookBuilder`·`LogbookCsv`와 같은 규칙 (DOM을 쓰지 않아 Node에서 시험) |
| `js/app.js` | 화면. 서버에서 온 글자는 `textContent`로만 넣는다 |
| `css/tokens.css` | `design/tokens/tokens.css` 복사본 (디자인이 바뀌면 다시 복사한다) |
| `css/app.css` | 스타일. 색·글자·간격·모서리는 tokens.css의 `drivelog_` 토큰만 쓰고, 앱과 같은 남색 다크 테마·Asta Sans(Google Fonts) |

## 보안

- 웹에는 공개 키만 들어갑니다. 무엇을 볼 수 있고 바꿀 수 있는지는 서버의 RLS가 정합니다 (`backend/README.md`).
- 관리자는 운행 기록을 고칠 수 없고, 차량을 지울 수도 없습니다(연결 해제만 가능).
- 로그인 정보는 브라우저 `localStorage`에 둡니다. 로그아웃은 이 브라우저에서만 합니다(같은 계정을 쓰는 다른 사람은 로그아웃되지 않음).

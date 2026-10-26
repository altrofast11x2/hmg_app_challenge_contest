# backend — Supabase (Postgres + Auth + RLS)

차량 앱과 관리자 웹이 함께 쓰는 서버입니다. 앱은 Supabase REST API를 직접 호출합니다.

| 경로 | 내용 |
|---|---|
| `supabase/migrations/0001_init.sql` | 테이블, RLS 정책, 연결 코드 발급·사용 함수 |
| `supabase/migrations/0002_hardening.sql` | 보안·성능 점검 반영: RLS 판단 함수를 API에 안 보이는 `private` 스키마로, 정책은 로그인 사용자에게만, 외래 키 인덱스 |
| `scripts/setup.mjs` | 마이그레이션 적용 + 익명 로그인 켜기 (Management API) |
| `seed/seed.mjs` | 시연용 데모 조직, 관리자 계정, 연결 코드 (secret 키 사용) |
| `seed/seed.sql` | `seed.mjs`와 같은 데모 데이터를 SQL로 (관리자 계정은 대시보드에서 먼저 만든다) |
| `.env.example` | 설정 예시. `backend/.env`로 복사해서 채운다 (**커밋 금지**) |

## 계정과 권한

| 계정 | 로그인 | 볼 수 있는 것 | 쓸 수 있는 것 |
|---|---|---|---|
| 관리자 (`admin`) | 이메일·비밀번호 (웹) | 조직의 차량, 운전자, 연결 코드, 모든 운행 | 연결 코드 발급, 차량 이름·기본 운전자, 운전자 추가 |
| 운전자 (`driver`) | 이메일·비밀번호 (웹, 선택) | 자기 운행 (`drivers.user_id`가 연결된 경우) | 없음 |
| 열람자 (`viewer`, M2) | 이메일·비밀번호 (웹) | 조직의 모든 운행 | 없음 (역할만 미리 만들어 둠) |
| 차량 | 익명 로그인 (비밀번호 없음) | 자기 조직 이름, 자기 운행 | 자기 운행·분류 이력 업로드 |

- 운행 기록은 **차량만** 쓸 수 있습니다. 관리자도 고칠 수 없어서 "고칠 수 없는 기록"이 서버에서도 유지됩니다.
- 차량은 관리자가 발급한 6자리 코드(`issue_pairing_code`, 10분 유효, 1회용)로 `pair_vehicle`을 불러 조직에 등록됩니다. 틀린 코드를 1시간에 10번 넘게 넣으면 막힙니다.
- 다른 조직에서 쓰는 차량은 그 조직 관리자가 차량을 삭제해야 옮길 수 있습니다.
- `service_role` 키는 `backend/.env`에서 seed 스크립트만 씁니다. 앱과 웹에는 공개(anon) 키만 들어갑니다.

## 처음 설정하기

방법은 두 가지다. A와 `setup.mjs`는 마이그레이션 적용 기록을 `public.drivelog_migrations`에 남겨서, 둘을 섞어 써도 같은 SQL을 두 번 적용하지 않는다.

### A. Claude Code + Supabase MCP (secret 키 없이)

1. 대시보드에서 프로젝트를 만들고, 프로젝트 ref로 MCP를 등록한다 (`.mcp.json`).
   ```
   claude mcp add --scope project --transport http supabase "https://mcp.supabase.com/mcp?project_ref=<project-ref>&features=docs%2Caccount%2Cdatabase%2Cdebugging%2Cdevelopment%2Cfunctions%2Cbranching"
   ```
2. Claude Code를 다시 시작하고 `/mcp`에서 supabase를 승인·로그인한다.
3. 대시보드에서 직접 할 일 (MCP에 해당 기능이 없다)
   - **Authentication > Sign In / Providers**에서 **Allow anonymous sign-ins** 켜기
   - **Authentication > Users > Add user > Create new user**로 관리자 계정 만들기 (**Auto Confirm User** 체크)
4. Claude가 MCP로 `migrations/`의 SQL을 차례로 적용(`apply_migration`) → `drivelog_migrations` 기록 → `seed/seed.sql` 실행 → 보안·성능 점검(advisors)을 한다.
   - `apply_migration`과 정책 삭제 같은 SQL은 Claude Code에 승인 창이 뜬다. 늦게 승인하면 요청이 만료되어 `Invalid or expired requestState` 오류가 난다 → 다시 실행하고 바로 승인한다.
5. `local.properties`에 `supabase.url`, `supabase.anonKey`(Publishable key)를 넣는다.

### B. 스크립트 (`backend/.env`의 키 사용)

1. https://supabase.com 에서 프로젝트를 만든다 (지역: Northeast Asia (Seoul) 권장).
2. `backend/.env.example`을 `backend/.env`로 복사하고 값을 채운다.
3. 마이그레이션 적용과 익명 로그인 켜기 — 둘 중 하나
   - 자동: `node backend/scripts/setup.mjs` (`SUPABASE_ACCESS_TOKEN` 필요)
   - 수동: 대시보드 **SQL Editor**에 `supabase/migrations/0001_init.sql` 내용을 붙여 넣고 실행 → **Authentication > Sign In / Providers**에서 **Allow anonymous sign-ins** 켜기
4. 데모 데이터: `node backend/seed/seed.mjs`
5. 앱 빌드 설정: 루트 `local.properties`에 `supabase.url`, `supabase.anonKey`를 넣는다 (`local.properties.example` 참고).

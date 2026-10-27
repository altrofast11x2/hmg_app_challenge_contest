# backend — Supabase (Postgres + Auth + RLS)

차량 앱과 관리자 웹이 함께 쓰는 서버입니다. 앱은 Supabase REST API를 직접 호출합니다.

| 경로 | 내용 |
|---|---|
| `supabase/migrations/0001_init.sql` | 테이블, RLS 정책, 연결 코드 발급·사용 함수 |
| `supabase/migrations/0002_hardening.sql` | 보안·성능 점검 반영: RLS 판단 함수를 API에 안 보이는 `private` 스키마로, 정책은 로그인 사용자에게만, 외래 키 인덱스 |
| `scripts/setup.mjs` | 마이그레이션 적용 + 익명 로그인 켜기 (Management API) |
| `seed/seed.mjs` | 시연용 데모 조직, 관리자 계정, 연결 코드 (secret 키 사용) |
| `supabase/migrations/0003_vehicle_admin_limits.sql` | 관리자는 차량 이름·기본 운전자만 수정, 차량 삭제 대신 `unpair_vehicle`(연결 해제) |
| `supabase/migrations/0004_developer_orgs.sql` | 개발자 계정(`developers`), 기업 정보 열, 개발자의 기업 등록 함수 `create_organization`(기업 관리자 계정도 만든다) |
| `supabase/migrations/0007_member_signup.sql` | 직원 회원가입: 기업 가입 코드(`organizations.join_code`), 가입 신청(`member_requests`, `member_sign_up`은 로그인 없이), 관리자 승인과 차량 배정(`decide_member_request`, `set_member_vehicles`, `vehicle_members`), 직원 내보내기, 가입 코드 바꾸기. 직원(driver)은 배정 차량만 보도록 차량·운행 권한을 좁힘. 개발자의 기업 삭제(`delete_organization`, 기업명 확인) |
| `supabase/migrations/0008_fixes.sql` | 2026-10-27 점검에서 고친 것: 앱에서 동의를 철회하면 차량이 스스로 연결을 해제(`unpair_own_vehicle`), 휴대폰으로 받기 링크는 그 기간 운행의 운전자 이름만, 틀린 가입 코드 시도 제한을 모든 기업 공통에서 접속 주소별(1시간 20번, 주소는 sha256만 하루 보관)로 |
| `supabase/migrations/0009_chain_id.sql` | 한 차량에 해시 체인이 여러 개일 수 있게: 실제 차(VIN)에서 동의 철회·앱 재설치 뒤 다시 연결하면 체인이 seq 1부터 새로 시작해 업로드가 409로 막히던 것을 고침. 운행·이력·체인 머리에 `chain_id`(체인 첫 기록의 해시), 고유 조건을 (차량, 체인 ID, seq)로. 기존 기록은 같은 규칙으로 채움 |
| `supabase/migrations/0006_share_links.sql` | 휴대폰으로 받기: 차량이 만드는 10분짜리 링크(`create_share_link`, 토큰은 sha256만 저장, 차량당 1시간에 20개)와 로그인 없이 그 링크로 읽는 `shared_logbook`. 보안 점검의 "anon이 SECURITY DEFINER 함수를 부를 수 있다"(shared_logbook)와 "정책 없는 RLS 테이블"(share_links)은 의도한 것이다 |
| `supabase/migrations/0005_vehicle_registration.sql` | 차량의 차종·자동차등록번호 (세무서 제출용 서식의 ①②). 관리자는 이 두 열도 고칠 수 있다 |
| `seed/seed.sql` | 개발자 계정, 데모 기업과 기업 관리자 계정, 시연용 연결 코드 (실제 값은 `backend/.env`) |
| `.env.example` | 설정 예시. `backend/.env`로 복사해서 채운다 (**커밋 금지**) |

## 계정과 권한

| 계정 | 로그인 | 볼 수 있는 것 | 쓸 수 있는 것 |
|---|---|---|---|
| 개발자 (`developers`) | 이메일·비밀번호 (웹) | 모든 기업 정보와 차량 수 (운행 기록은 볼 수 없음) | 기업 등록과 기업 관리자 계정 만들기 (`create_organization`) |
| 관리자 (`admin`) | 이메일·비밀번호 (웹) | 조직의 차량, 운전자, 연결 코드, 모든 운행 | 연결 코드 발급, 차량 이름·기본 운전자·차종·자동차등록번호, 차량 연결 해제, 운전자 추가 |
| 직원 (`driver`) | 이메일·비밀번호 (웹, 기업 가입 코드로 직접 가입 → 관리자 승인) | 배정받은 차량의 운행 (`vehicle_members`) | 없음 |
| 열람자 (`viewer`, M2) | 이메일·비밀번호 (웹) | 조직의 모든 운행 | 없음 (역할만 미리 만들어 둠) |
| 차량 | 익명 로그인 (비밀번호 없음) | 자기 조직 이름, 자기 운행 | 자기 운행·분류 이력 업로드 |

- 운행 기록은 **차량만** 쓸 수 있습니다. 관리자도 고칠 수 없어서 "고칠 수 없는 기록"이 서버에서도 유지됩니다.
  - 관리자는 차량의 이름과 기본 운전자만 바꿀 수 있습니다. 차량 계정(`device_user_id`)은 바꿀 수 없어서, 관리자가 자기 계정을 차량으로 등록해 운행을 고칠 수 없습니다.
  - 차량은 지울 수 없습니다(지우면 운행 기록이 함께 지워지므로). 대신 `unpair_vehicle`로 연결만 해제하고 운행 기록은 남깁니다.
- 차량은 관리자가 발급한 6자리 코드(`issue_pairing_code`, 10분 유효, 1회용)로 `pair_vehicle`을 불러 조직에 등록됩니다. 틀린 코드를 1시간에 10번 넘게 넣으면 막힙니다.
- 다른 조직에서 쓰는 차량은 그 조직 관리자가 연결을 해제해야 옮길 수 있습니다.
- `service_role` 키는 `backend/.env`에서 seed 스크립트만 씁니다. 앱과 웹에는 공개(anon) 키만 들어갑니다.

## 처음 설정하기

방법은 두 가지다. A와 `setup.mjs`는 마이그레이션 적용 기록을 `public.drivelog_migrations`에 남겨서, 둘을 섞어 써도 같은 SQL을 두 번 적용하지 않는다.

### A. Claude Code + Supabase MCP (secret 키 없이)

1. 대시보드에서 프로젝트를 만들고, 프로젝트 ref로 MCP를 등록한다 (`.mcp.json`).
   ```
   claude mcp add --scope project --transport http supabase "https://mcp.supabase.com/mcp?project_ref=<project-ref>&features=docs%2Caccount%2Cdatabase%2Cdebugging%2Cdevelopment%2Cfunctions%2Cbranching"
   ```
2. Claude Code를 다시 시작하고 `/mcp`에서 supabase를 승인·로그인한다.
3. 대시보드에서 직접 할 일 (MCP에 해당 기능이 없다): **Authentication > Sign In / Providers**에서 **Allow anonymous sign-ins** 켜기
4. Claude가 MCP로 `migrations/`의 SQL을 차례로 적용(`apply_migration`) → `drivelog_migrations` 기록 → `seed/seed.sql`(관리자 계정 포함, 값은 `backend/.env`) 실행 → 보안·성능 점검(advisors)을 한다.
   - 관리자 계정을 대시보드(**Authentication > Users > Add user**, **Auto Confirm User** 체크)에서 직접 만들어도 된다. 그때는 `seed.sql`의 비밀번호 칸을 그대로 둔다.
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

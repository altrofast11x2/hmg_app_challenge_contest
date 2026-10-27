# 작업 인수인계

> 마지막 갱신: 2026-10-26 · 세션 ID는 `transcript/SESSION_ID.txt`

## 지금 상태

| 단계 (SPEC.md 11번) | 상태 |
|---|---|
| 1. 프로젝트 생성 | 완료: assembleDebug와 단위 테스트 성공, `Pleos_Connect_v2.1`에 설치·실행 확인 |
| 2. 환경 점검 (GMS 확인, 문서 읽기, 구현 계획과 백엔드 제안) | 완료 (GMS 없음 → Supabase 결정) |
| 3. SDK 연결과 debug 확인 화면 | 완료: 기어·주행 제한·계기판·잔량·주소를 에뮬레이터와 Virtual Console에서 검증. 결과는 `docs/sdk-usage.md` |
| 4. Room, 운행 감지 상태 머신, 해시 체인, 단위 테스트 | 완료: 단위 테스트 28개 통과, 에뮬레이터에서 분류 완료·미분류 자동 종료·강제 종료 후 복원 검증. 해시 규칙은 `docs/integrity.md` |
| 5. 동의, 주행 중 화면, 정차 후 분류, 월별 기록부, CSV, 라이선스 화면 | 구조 완료 (디자인 토큰 대기). 단위 테스트 36개, 에뮬레이터에서 전체 흐름 검증 |
| 6. 백엔드(Supabase) 스키마·RLS, 연결 코드, 동기화 | 코드 완료 (단위 테스트 50개). Supabase 프로젝트(ref `ggpychjlddrgbakxjyew`)에 MCP로 0001~0003 적용, advisors 점검, seed.sql로 데모 조직·관리자(`admin@email.com`, 비밀번호는 `backend/.env`)·연결 코드 `422511` 생성, 관리자 로그인·RLS를 실제 API로 확인. **남은 것: 사용자가 대시보드에서 익명 로그인 켜기 → 에뮬레이터에서 연결·동기화 시험** |
| 7. web/ 관리자 최소판 | 구현 완료(로그인, 운행기록부·CSV, 차량·연결 해제, 연결 코드 발급). 웹 단위 테스트 8개, Node로 API 연동 확인, headless Edge로 화면 확인. **남은 것: 실제 운행 데이터로 표 확인, 배포(어디에 올릴지 사용자 확인)** |
| 8. GitHub, release APK, README, docs 4종 | 완료: `apk/AltroStudio_DriveLog_release.apk`(v2 서명, 에뮬레이터 설치·실행 1.3~1.6초), README(심사용 빠른 시작·APK·개발자 메모·라이선스), `docs/status.md`·`demo-script.md`·`business.md`, `sdk-usage.md` 갱신 |
| 디자인 v0.1 | A01~A03 명세대로, 나머지 화면은 토큰만 (커밋 d13b8a1). 명세와 다른 점은 `design/REQUESTS.md`. 관리자 웹도 같은 토큰·Asta Sans·다크 테마 |
| 실서버 시험 (2026-10-26) | 에뮬레이터 release 앱: 시연용 코드 연결 → 운행 3건 → 서버 업로드·비업무 주소 삭제·해시 체인 확인, 웹(기업 계정)에 표시. Virtual Console HTTP는 브라우저 heartbeat(`POST /browser/session?client=…`, 1초)가 끊기면 `emulator_inactive` → 직접 heartbeat를 보내며 시험했다 |
| 9. 제출 직전 점검 | 점검표 보고함 (2026-10-26). 새 AVD(같은 Pleos 이미지로 새로 만듦)에 release APK 설치 → 동의 → 코드 422511 연결 → 주행 1건 → 서버 반영 확인 후, 시험 차량·AVD는 지움. release lint 오류 2건(LocalActivity, CSV BOM 문자) 고치고 APK 다시 빌드. 단위 55개·웹 10개 통과, 전체 이력 비밀값 검사 통과. `docs/plan-summary.md`(기획서 요약) 추가. **남은 것: 사용자 결정 항목(공개 범위, 데모 비밀번호 전달, LICENSE 저작자, 문의처, 옛 브랜치)과 Secrets 등록** |
| 웹 기간 선택 (2026-10-27) | 사용자 요청: 관리자 웹 운행기록부에 `input type="date"` 두 칸으로 기간(시작~끝, 366일까지) 고르기. 달 고르기·◀ ▶는 그대로, CSV 파일 이름에 기간. 웹 테스트 12개 |
| 앱 기간 선택 (2026-10-27) | 사용자 요청: 운행기록부 제목 줄 [기간 선택] → [시작·끝 날짜] 칸 + Material 3 DatePicker(한 달씩, 연도 선택, 사용자가 웹 날짜 입력 모양을 원함) 대화상자. 날짜 칸이 최소 터치 80dp가 되도록 약 2배 확대, 색은 토큰. material3 1.3.0은 바깥에서 displayedMonthMillis를 바꾸면 날짜 칸이 안 움직여서 key()로 달력을 새로 그린다. 기간 모델 `LogbookPeriod`(웹 pickPeriod와 같은 규칙), CSV 파일 이름도 웹과 같음. 단위 테스트 59개 |
| 세무 제출 (2026-10-27) | 사용자 제안("기관 제출") → 확인 결과 운행기록부는 보관하다가 세무서 요구 시 제출, 신고 때는 명세서 제출. 그래서 ① 관리자 웹 "세무 제출" 탭: 국세청고시 제2025-8호 별지 서식(법제처 PDF로 칸 확인)과 같은 칸의 차량별 서식 인쇄·PDF, ② 명세서용 차량별 업무사용비율 표·CSV, ③ 앱: 연결한 차량은 CSV 대신 관리자 웹 안내. 0005로 차량에 차종·자동차등록번호. 서식 미리보기는 라이트 테마 토큰으로 바꿔 보여 준다 |

- 8단계 release 시험에서 찾아 고친 버그 (6단계 코드가 실제 기기에서 서버와 통신한 적이 없었다)
  1. 서버 주소·키를 `BuildConfig` 상수로 넣었더니 Kotlin이 값을 코드에 복사(인라인)해서, local.properties를 채운 뒤에도 증분 빌드에 빈 값이 남았다 → 문자열 리소스(`resValue`)로 바꿔 실행 중에 읽는다
  2. 매니페스트에 `INTERNET` 권한이 없었다 → 추가
  3. 연결 중 `SecurityException`이 나자 앱이 죽었다 → 연결·동기화는 어떤 예외든 실패로 알리고 멈추지 않는다 (단위 테스트 2개 추가, 총 52개)
- release 서명 키: `C:\Users\kazin\.android\drivelog-release.jks` (저장소 밖), 비밀번호는 git 제외 파일 `keystore.properties`. **사용자가 둘 다 따로 백업해야 한다** (잃으면 같은 앱으로 업데이트 불가).
- 관리자 웹의 연결 코드 발급은 차량 탭 안에 있다 (앱 안내 "관리자 웹 › 차량 › 연결 코드 발급", A02 명세).
- 관리자 웹 배포: https://altrostudio-drivelog.vercel.app (Vercel 프로젝트 `altrofast11x2s-projects/altrostudio-drivelog`). Vercel MCP는 프로젝트 생성 403이라 CLI로 배포한다: `node web/scripts/build.mjs` → `vercel deploy --cwd web/dist --prod --yes` (CLI는 사용자 계정으로 로그인됨, `web/dist/.vercel`에 연결 정보). 배포된 파일이 원본과 같은지 sha1로 대조했다.
- 디자인 쪽 공유 채팅(claude.ai/share/f646197e-…)은 Cloudflare 봇 확인에 막혀 읽지 못함 → 사용자에게 붙여 넣기를 부탁함.

- 6단계 구조: `backend/supabase/migrations/0001_init.sql`(테이블·RLS·`issue_pairing_code`·`pair_vehicle`), `backend/scripts/setup.mjs`(Management API로 SQL 적용·익명 로그인 켜기), `backend/seed/seed.mjs`(데모 조직·`admin@email.com`·재사용 데모 코드). 앱은 `data/remote`(OkHttp), `data/sync`(연결·동기화·WorkManager), `ui/connect`(A02 구조).
- 동의문 버전 2: 조직 연결 시 서버 제공 내용을 추가해서 기존 동의자에게 다시 동의를 받는다.
- 연결 화면에 명세에 없는 [나중에 연결]과 제공 고지 한 줄을 넣었고 `design/REQUESTS.md`에 알렸다.

- 5단계 화면은 "구조 먼저"로 만들었다. 색·글자는 Material 테마 이름으로만, 간격·크기는 `ui/theme/Dimens.kt`로만 참조한다. 디자인 토큰이 오면 `Color.kt`·`Type.kt`·`Dimens.kt`·`Theme.kt`만 바꾼다 (`design/REQUESTS.md`에 요청함).
- 디자인은 기능을 모두 만든 뒤에 입힌다 (2026-10-26 사용자 결정).
- 에뮬레이터 시간대를 Asia/Seoul로 바꿨다 (`adb shell service call alarm 3 s16 Asia/Seoul`, 자동 시간대 끔). 새 AVD를 쓰면 다시 해야 한다.
- 2분할(Portrait) 배치는 `adb shell wm density 320`으로 앱 창을 841dp로 만들어 확인했다 (`wm size`는 Pleos 홈이 앱 창 크기를 바꾸지 않고 잘라 보여서 시험이 안 된다). 확인 후 `wm density reset`.
- 2026-10-26부터 **main 브랜치에서 작업**한다 (altrofast11x2를 main에 합침). main의 `.github/workflows/supabase-keepalive.yml`이 하루 두 번 서버에 조회를 보낸다. 저장소 Secrets `SUPABASE_URL`·`SUPABASE_PUBLISHABLE_KEY`는 2026-10-27 사용자 요청으로 `local.properties` 값을 `gh secret set`으로 넣었고, 수동 실행에서 HTTP 200을 확인했다.
- 2026-10-27 사용자 결정(9단계 추천안 수락): 저장소는 비공개로 두고 심사용 계정이 공지되면 Collaborator로 초대한다 (공개하면 `claude_chat/transcript/` 대화 기록과 이메일이 이력째 공개됨). 기업 계정 비밀번호만 기획서에 적고, 개발자 계정 비밀번호는 무작위 값으로 바꿨다가, 2026-10-27 사용자 지시로 사용자가 정한 값으로 다시 바꿨다(`backend/.env`, Auth API로 변경). LICENSE 저작자는 AltroStudio. 옛 브랜치 `altrofast11x2`는 main에 포함돼 지워도 되지만 Claude Code 권한 판단에서 막혀 사용자가 지운다. `nrioig`는 팀원 브랜치라 지우지 않는다(내 삭제 제안은 잘못이었음). 개인정보 처리방침 문의처는 2026-10-27 사용자 지시로 `kazinobel9807@gmail.com`.
- **제출 전 점검: Supabase 무료 프로젝트는 7일 동안 사용이 적으면 일시 정지된다** (정지 1주 전 경고 메일, 정지 후 90일 안에 대시보드에서 재개). 심사 기간(11/15~12/4)에 정지되지 않게 할 방법을 정해야 한다. 정지되면 API가 540을 돌려주고, 앱은 서버 오류로 보고 이 차량에만 기록한다.
- 계정 (2026-10-26 사용자 지정, 비밀번호는 `backend/.env`): **개발자** `AltroStudio@altro.com`(관리자 웹에서 기업 등록, 운행은 못 봄) · **기업(데모)** `demo@altro.com`(데모 기업 관리자). 웹은 아이디를 소문자로 바꿔 로그인한다. 개인정보 처리방침 문의처는 `kazinobel9807@gmail.com`(2026-10-27 사용자 지시). `altro.com`은 남의 도메인이라 메일 기능(비밀번호 재설정 등)은 쓰지 않는다.
- Virtual Console은 브라우저 없이 `curl -X PUT "http://127.0.0.1:8088/vehicle/gear?gear=Drive"` 같은 HTTP API로 조작할 수 있다 (`memory/pleos-dev-environment.md`).

## 결정 사항

- 저장소: 비공개 `altrofast11x2/hmg_app_challenge_contest`의 `altrofast11x2` 브랜치. 이전 내용은 `altrofast11x2_backup`에 백업했다.
- `altrofast11x2` push는 매번 사용자에게 확인받는다.
- 패키지 `com.altrostudio.drivelog`, 앱 이름 "자동 운행기록부", minSdk 28, compileSdk/targetSdk 35, Gradle 8.10.2 / AGP 8.8.2 / Kotlin 2.0.0, Java 17
- Gradle wrapper는 공식 8.10.2 배포판으로 생성했고, `distributionSha256Sum`으로 배포판 체크섬을 검증한다.
- 앱 아이콘은 직접 그린 벡터다 (외부 아이콘 미사용).
- 2단계 결정 (2026-10-26, 추천안 전부 수락)
  1. 백엔드 Supabase. 차량은 익명 로그인 + 6자리 연결 코드로 조직에 등록
  2. Room + WorkManager + OkHttp·kotlinx.serialization (supabase-kt 미사용), 수동 DI
  3. 운행 종료: P에서 분류 탭 즉시, 또는 P 5분 지속 시 미분류로 종료 (신호 대기·Auto Hold 제외, 데모용 시간 조절)
  4. 주차 브레이크를 정차 판단 보조로 사용 (Pleos 경고등 조회 vs AAOS `PARKING_BRAKE_ON`은 3단계에서 비교)
  5. 경로 없는 주행의 주소는 NaviHelper만 사용, 없으면 방문처 빈칸 → 웹에서 수정
- 3단계 결정 (2026-10-26)
  - 전비 API는 에뮬레이터 미지원 → 운행 전후 배터리 잔량(%) 기록, 실차에서 전비가 되면 함께 기록
  - VIN이 없으면(에뮬레이터는 "invalid") 앱이 만든 기기 ID로 차량 구분, 웹에 "VIN 미확인"
  - 주차 브레이크는 AAOS `PARKING_BRAKE_ON` (Pleos 경고등은 권한 거부), `CAR_SAFETY` 미사용
- 디자인은 design/ 폴더 기준(2026-10-26)

## 이 PC에서 빌드할 때

- Claude Code 세션의 JAVA_HOME은 JBR 21을 가리킨다. 명령줄 빌드는 JDK 17을 직접 지정한다.
  ```bash
  export JAVA_HOME="/c/Users/kazin/AppData/Local/Programs/Eclipse Adoptium/jdk-17.0.20.1+1"
  ./gradlew assembleDebug
  ```
- 그 밖의 환경 함정은 `memory/pleos-dev-environment.md`에 있다.

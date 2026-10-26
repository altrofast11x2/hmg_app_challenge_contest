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
| 6. 백엔드(Supabase) 스키마·RLS, 연결 코드, 동기화 | 코드 완료 (단위 테스트 50개). **사용자가 Supabase 프로젝트를 만들고 `backend/.env`·`local.properties`를 채우면** setup·seed 실행과 실제 연결·동기화 시험 |
| 7. web/ 관리자 최소판 | 계획 |
| 8~9 | 계획 |

- 6단계 구조: `backend/supabase/migrations/0001_init.sql`(테이블·RLS·`issue_pairing_code`·`pair_vehicle`), `backend/scripts/setup.mjs`(Management API로 SQL 적용·익명 로그인 켜기), `backend/seed/seed.mjs`(데모 조직·`admin@email.com`·재사용 데모 코드). 앱은 `data/remote`(OkHttp), `data/sync`(연결·동기화·WorkManager), `ui/connect`(A02 구조).
- 동의문 버전 2: 조직 연결 시 서버 제공 내용을 추가해서 기존 동의자에게 다시 동의를 받는다.
- 연결 화면에 명세에 없는 [나중에 연결]과 제공 고지 한 줄을 넣었고 `design/REQUESTS.md`에 알렸다.

- 5단계 화면은 "구조 먼저"로 만들었다. 색·글자는 Material 테마 이름으로만, 간격·크기는 `ui/theme/Dimens.kt`로만 참조한다. 디자인 토큰이 오면 `Color.kt`·`Type.kt`·`Dimens.kt`·`Theme.kt`만 바꾼다 (`design/REQUESTS.md`에 요청함).
- 디자인은 기능을 모두 만든 뒤에 입힌다 (2026-10-26 사용자 결정).
- 에뮬레이터 시간대를 Asia/Seoul로 바꿨다 (`adb shell service call alarm 3 s16 Asia/Seoul`, 자동 시간대 끔). 새 AVD를 쓰면 다시 해야 한다.
- 2분할(Portrait) 배치는 `adb shell wm density 320`으로 앱 창을 841dp로 만들어 확인했다 (`wm size`는 Pleos 홈이 앱 창 크기를 바꾸지 않고 잘라 보여서 시험이 안 된다). 확인 후 `wm density reset`.
- 관리자 계정 이메일은 `admin@email.com` (사용자 지정). 개인정보 처리방침 문의처도 이 주소로 넣었다. **제출 전 점검: 문의처를 실제 연락처로 바꿀지 확인** (email.com은 실제 메일 서비스 도메인이다).
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

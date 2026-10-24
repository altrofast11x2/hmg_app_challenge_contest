# 작업 인수인계 (Claude Code 대화 요약)

> 마지막 갱신: 2026-10-24 · 원본 세션 ID: `63f35f8b-b151-42be-a526-b897e5c6ee6a`
> 다른 컴퓨터에서 Claude Code에게 "claude_chat/HANDOFF.md 읽고 이어서 하자"라고 하면 이 문서만으로 이어갈 수 있다.

## 1. 프로젝트

- **현대자동차그룹 차량용 앱 개발 공모전** (Code your App into Pleos App Market): https://appdevchallenge.auto-hmg.io
- 대상 플랫폼: Pleos Connect (Android Automotive OS 기반 IVI), Pleos Connect SDK
- 일정
  | 날짜 | 내용 |
  |---|---|
  | 10/28 | 온라인 설명회 |
  | **11/15 23:59** | 접수 마감 (기획서 필수, 시연 영상·GitHub(소스+APK)은 선택이며 가산점) |
  | 11/23 | 1차 결과 (20팀) |
  | 12/3~4 | 2차 오프라인 PT. **실제 동작하는 앱 필수**. 장소: 현대차 강남대로사옥 1층 UX스튜디오 서울 |
  | 12/18 | 최종 10팀 발표 (이후 추가 개발은 순위에 반영되지 않음) |
  | 2027.02 | 현대차그룹 행사에서 전시·시상 |
- 심사 기준: 차량특화성 · 창의성 · 기술 활용성(Pleos SDK) · 사업성(타깃, MAU, Retention). 배점은 공개하지 않음
- 접수양식(=기획서) 항목: 한 줄 소개 / 기획 배경 / 주요 기능·차량 특화 요소 / 사용 시나리오 / 개발 계획(SDK·API 사용 계획) / 기대효과·발전 가능성 / 참고자료 / 숏폼 링크
- 산출물은 목업·프로토타입·화면 녹화도 인정. 비공개 저장소로 낼 경우 심사 계정을 Collaborator로 초대해야 함

## 2. 사용자 선호·지시 (반드시 지킬 것)

- 모든 설명은 **한국어**로 한다.
- 작업 브랜치는 **`altrofast11x2`**. `main`에 직접 push하지 않는다. 원격 `main`에는 팀원이 README를 수정한 커밋(cf64586)이 있어 합칠 때 README가 충돌한다.
- 외부 LLM 등의 **API 키는 앱에 직접 넣어도 된다** (private 저장소, 나중에 교체 예정). 보안 경고를 반복하지 않는다. 키는 아직 받지 않았다.
- **테스트로 확인한 권한은 지우지 않는다.** 지금은 stash/패치에 보관 중이다 (4번 참고).
- **아이디어가 정해지기 전에는 기능 개발을 시작하지 않는다.** 사용자가 "개발하자"고 하면 시작한다.

## 3. 개발 환경 (가이드 요구사항 충족 확인 완료)

- 저장소 위치: `C:\dev\hmg_app_challenge_contest` (OneDrive나 한글 경로는 Gradle 문제로 피함)
- JDK 17 (Temurin 17.0.20.1), Android Studio 2026.2.1, Android SDK `%LOCALAPPDATA%\Android\Sdk`
- Gradle 8.10.2 / AGP 8.8.2 / Kotlin 2.0.0 / Compose, compileSdk·targetSdk 35, minSdk 28
- Pleos SDK (Maven `https://nexus-playground.pleos.ai/repository/maven-releases/`): Vehicle 2.3.2, NaviHelper 3.0.5, TTS/STT 3.3.1, FusedLocation 1.1.3
- AVD `Pleos_Connect_v2.1`: 이미지 `system-images;android-34;pleos_car_emulator;x86_64` (v2.1.0, rev 2), Automotive, 콜드 부트, RAM 4GB, GPU host
- SDK Update Site (Windows): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
- Virtual Console: `adb forward tcp:8088 tcp:8088` 후 http://127.0.0.1:8088 (에뮬레이터에 내장된 `ai.pleos.connectwebconsole` 앱)
- 주의할 점
  - Nexus에는 Vehicle 2.3.1이 없다. 2.3.2를 쓴다.
  - Pleos 이미지는 `cmdline-tools\19.0`의 sdkmanager로 설치한다 (`latest`는 사용자 저장소를 읽지 않음).
  - Git Bash에서 adb를 쓸 때는 `MSYS_NO_PATHCONV=1`을 설정한다.
  - 세션에 JAVA_HOME이 없으면 사용자 환경변수에서 가져와 설정한다.
  - 에뮬레이터는 **user 빌드**라 `adb root`, VHAL dump, `cmd car_service` 디버그 명령을 쓸 수 없다.
  - AAOS 운전자 사용자 ID는 **10**이다. 런타임 권한은 `pm grant --user 10 ...`으로 준다.

## 4. 코드 상태

- GitHub `altrofast11x2` = `b68e08a` (기본 프로젝트 세팅). 로컬도 이 상태로 리셋했다.
- 보관 중인 변경 (원래 컴퓨터의 `git stash@{0}`, 이 폴더의 `patches/stash-0.patch`)
  - `app/src/debug/AndroidManifest.xml`: 테스트로 확인한 권한 (디버그 빌드 전용)
  - `app/build.gradle.kts`: `useLibrary("android.car")` (속도·기어를 읽는 AAOS Car API)
  - 개발을 시작할 때 `git apply claude_chat/patches/stash-0.patch`로 되살리고, `CAR_SPEED` 권한을 다시 준다:
    `adb shell pm grant --user 10 com.hmgchallenge.app android.car.permission.CAR_SPEED`
- 에뮬레이터 진단 화면 코드 백업: `backup/diagnostics/` (DiagnosticsActivity.kt + 매니페스트)

## 5. 에뮬레이터 실측 결과 (2026-10-24)

| 구분 | 결과 |
|---|---|
| 차량 제어 ✅ | 도어 잠금, 창문, 공조 전원·온도, 시트 열선, 앰비언트 조명. Virtual Console과 시스템 UI에도 반영됨 |
| 차량 조회 ✅ | 속도(AAOS `PERF_VEHICLE_SPEED`), 기어, 외기온도, 브레이크, 파킹브레이크, 방향지시등, 와이퍼, EV 배터리, 경고등, 좌석 착석, 회생제동 단계(`EV_BRAKE_REGENERATION_LEVEL`) |
| 불가 ❌ | 시트 위치 제어(눕히기). Seat API는 조회와 열선·통풍만 지원 |
| 에뮬레이터 미지원 | 타이어 공기압, 주행 모드 |
| ADAS·주변 차량 ❌ | ADAS 서비스(`IAdasService`)는 바인딩되지만 공개 클라이언트 SDK가 없다. AAOS `READ_ADAS_STATES`는 시스템 전용 권한. Virtual Console의 주변 차량은 그림일 뿐이다 |
| 차 밖 원격 제어 | 공개 API 없음 |

- 주의: 영역(area)은 항상 `check*Capabilities`로 먼저 얻는다. 도어 잠금은 ROW_1_LEFT 단독 영역을 지원하지 않고 4도어 묶음만 지원한다. 온도 설정은 공조 전원이 켜져 있어야 한다.
- Vehicle SDK의 권한은 에뮬레이터에서 모두 normal 수준이다. 실제 앱마켓에서는 승인받은 범위만 쓸 수 있다.

## 6. LLM 관련 조사

- Pleos **LLM SDK는 2026-10-15 제공 종료**됐다 (Nexus에는 2.1.3.2가 남아 있지만 쓰지 않는다). 대안은 **Gleo Interface**다 (`assets/schema.json` function calling, Pleos 검토·승인 필요).
- 외부 LLM API를 직접 쓰는 것에 대해서는 허용·금지 문구를 공개 문서에서 찾지 못했다. Pleos 문서가 외부 서버 연동을 전제하고 있어서 가능하다고 판단했다. 공모전 Q&A 게시판은 읽지 못했다. 사용자는 "외부 LLM 사용 가능하다고 나와 있다"고 했다.

## 7. 아이디어 후보 (미정, 사용자가 고민 중)

1. **멀미 제로** ⭐추천: 전기차 회생제동 멀미의 위험도를 예측하고 공조로 완화한다. 운전자 코칭과 LLM 놀이로 아이의 시선을 돌린다. 속도·회생제동 단계·공조 제어가 실측으로 가능함을 확인했다.
2. **로드 도슨트**: 경로 앞 장소를 LLM이 음성으로 설명하고 경유지로 추가한다. TourAPI를 함께 쓰고, 렌터카·관광 B2B로 확장한다.
3. **졸음 방어선**: 단조로운 주행을 감지하면 LLM이 대화로 깨우고 졸음쉼터를 경유지로 넣는다. 주차 후에는 휴식 모드(시트 눕힘 없이)로 전환한다.
4. **펫 모드**: 정차 중 반려동물을 위해 공조를 유지하고, 센터 화면에 안내를 띄운다.
5. **와이퍼 기상청**: 와이퍼·외기온도를 모아 도로 날씨 지도를 만든다 (데이터 사업).
- 그 전에 제안한 것: 쉼표(휴식 모드), 여행 퀘스트, 차량 주치의, 드라이브 무드
- 주행 중에는 음성 위주로, 차량 제어는 정차 중에 한다 (주행 중 도어·시트·차량 설정 조작과 동영상은 제한).

## 8. 남은 일

- [ ] 사용자가 아이디어를 정하면 → 기획서 초안(접수양식 1~6번)과 최소 시연 버전을 개발한다
- [ ] 개발을 시작할 때 `patches/stash-0.patch`를 적용하고 `CAR_SPEED`를 다시 준다
- [ ] API 키(LLM 등)를 받으면 서비스별로 최소 요청을 보내 동작을 확인한다
- [ ] 에뮬레이터 안의 테스트 녹화 파일을 삭제한다: `adb shell rm /data/local/tmp/diag.mp4`
- [ ] 제출 전에 이 `claude_chat` 폴더를 저장소에서 뺄지 결정한다 (심사위원·팀원이 대화 기록을 볼 수 있음)

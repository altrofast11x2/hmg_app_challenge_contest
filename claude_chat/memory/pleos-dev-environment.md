---
name: pleos-dev-environment
description: "이 PC의 Pleos Connect 개발환경 구성과 문서상 함정(존재하지 않는 Vehicle 2.3.1, sdkmanager 사용자 저장소 미지원)"
metadata:
  node_type: memory
  type: reference
  originSessionId: e32737fb-6845-4701-9a14-453c509dc039
  modified: 2026-10-24T13:50:57.196Z
---

2026-10-22에 구축하고 실제 에뮬레이터에서 동작까지 검증한 구성:
- JDK 17: `%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-17.0.20.1+1` (사용자 환경변수 JAVA_HOME)
- Android SDK: `%LOCALAPPDATA%\Android\Sdk` (ANDROID_HOME). Android Studio 2026.2.1은 winget으로 설치
- AVD `Pleos_Connect_v2.1`: 이미지 `system-images;android-34;pleos_car_emulator;x86_64`, 디바이스 `Pleos_Connect_v2_0`, cold boot, RAM 4096, gpu host, audioInput yes. 가속은 WHPX(Hyper-V가 켜져 있음)
- Virtual Console: `adb forward tcp:8088 tcp:8088` 후 http://127.0.0.1:8088/

함정:
- Pleos 문서의 시작 가이드와 공식 샘플(pleos-sample-vehicle-app)은 `Vehicle:2.3.1`을 쓰지만, Nexus 저장소에는 2.0.3과 2.3.2만 있다. 2.3.2를 사용한다.
- cmdline-tools `latest`(23.0)의 sdkmanager는 신형 `android` CLI로 위임되며 `~/.android/repositories.cfg` 사용자 저장소를 읽지 않는다. Pleos 이미지는 `cmdline-tools\19.0\bin\sdkmanager.bat`으로 설치한다.
- Git Bash에서 adb의 `/sdcard/...` 경로는 MSYS 경로 변환으로 망가진다. `MSYS_NO_PATHCONV=1`을 설정한다.
- 에뮬레이터 로그의 "Failed to load opengl32sw"는 INFO 수준이라 무시해도 된다.
- JAVA_HOME·ANDROID_HOME은 사용자 환경변수에 있지만, 그보다 먼저 열린 터미널에서 시작한 Claude Code 세션의 프로세스에는 들어오지 않는다. 그러면 gradlew가 "JAVA_HOME is not set"으로 실패하므로 `$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User')`로 먼저 설정한다.
- Windows PowerShell 5.1의 `Get-Content`는 UTF-8 한글을 CP949로 읽어 깨져 보인다. 파일 자체는 정상이므로 `-Encoding UTF8`이나 Read 도구로 확인한다.

- Pleos LLM SDK(`ai.pleos.playground:LLM`)는 2026-10-15 릴리스 노트에서 제공이 종료됐다. Nexus에는 2.1.3.2가 남아 있지만 쓰지 않는다. Gleo AI 연동은 Gleo Interface(`assets/schema.json`, Pleos 검토·승인 필요)로 한다. 외부 LLM API 직접 호출은 2026-10-24 기준 공개 문서에서 허용·금지 문구를 찾지 못했다(Q&A 게시판은 읽지 못함).
- 속도·기어·외기온도·연료 레벨은 Vehicle SDK가 아니라 AAOS Car API로 얻는다 (Pleos 'SDK API 정책' 문서).

- 에뮬레이터 실측 결과(2026-10-24, 디버그 진단 화면 `app/src/debug/.../DiagnosticsActivity.kt`):
  - 앱에서 제어되는 것: 도어 잠금, 창문, 공조 전원·온도, 앰비언트 조명, 시트 열선. 결과가 Virtual Console(`/vehicle/state`)에도 반영된다.
  - 영역 함정: 도어 잠금은 ROW_1_LEFT 단독 영역을 지원하지 않는다(4도어 묶음만 지원). 그래서 항상 `check*Capabilities`로 지원 영역을 먼저 얻는다. 온도 설정은 공조 전원이 꺼져 있으면 PropertyNotAvailable로 실패한다.
  - 시트 위치(등받이 등)는 SDK에서 조회만 되고 제어는 안 된다. 타이어 공기압과 주행 모드는 에뮬레이터에서 지원하지 않는다.
  - ADAS: 서비스(`IAdasService`)는 바인딩되지만 공개 클라이언트 SDK가 없다. AAOS의 `READ_ADAS_STATES`는 signature 권한이다. Virtual Console의 주변 차량은 그림일 뿐이다. → 주변 차량 센서 데이터는 쓸 수 없다.
  - `CAR_SPEED`는 런타임 권한이다. AAOS 운전자 사용자가 10이므로 `pm grant --user 10`으로 부여한다.
  - 에뮬레이터는 user 빌드라 `adb root`, VHAL dump, `cmd car_service get-carpropertyconfig`를 쓸 수 없다.

관련: [[contest-dev-repo-location]]

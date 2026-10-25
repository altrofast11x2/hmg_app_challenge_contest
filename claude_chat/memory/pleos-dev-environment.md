---
name: pleos-dev-environment
description: "이 PC의 Pleos Connect 개발환경과 함정 (JAVA_HOME이 JBR 21, Vehicle 2.3.1 없음, adb는 user 10, MSYS 경로 변환 등)"
metadata:
  node_type: memory
  type: reference
  originSessionId: a0acef44-095f-4903-afed-69862aa322b3
  modified: 2026-10-25T15:16:00.752Z
---

2026-10-22~24에 이전 폴더(C:\dev\hmg_app_challenge_contest)에서 검증하고 10-26에 DriveLog에서 다시 확인한 구성:
- JDK 17: `%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-17.0.20.1+1` (사용자 환경변수 JAVA_HOME). 그런데 Claude Code 세션 프로세스의 JAVA_HOME과 PATH의 java는 `~/.jdks/jbr-21.0.11`이다. gradlew를 실행할 때는 JAVA_HOME을 JDK 17로 직접 지정한다.
- Gradle 8.10.2 공식 배포판이 `~/.gradle/wrapper/dists/gradle-8.10.2-bin/a04bxjujx95o3nb99gddekhwo/`에 있다 (zip sha256 = 공식 31c55713…). 시스템 gradle은 없다.
- AVD `Pleos_Connect_v2.1`: 이미지 `system-images;android-34;pleos_car_emulator;x86_64`, cold boot, RAM 4GB. 화면 2560x1440이고, 앱은 홈 화면의 오른쪽 영역(왼쪽은 시스템 DrivingView)에 뜬다.
- AAOS 운전자 사용자 ID는 10이다. 런타임 권한은 `pm grant --user 10 ...`으로 준다. 에뮬레이터는 user 빌드라 `adb root`, VHAL dump, `cmd car_service` 디버그 명령을 쓸 수 없다.
- Virtual Console: `adb forward tcp:8088 tcp:8088` 후 http://127.0.0.1:8088/
- Git Bash에서 adb 경로(/sdcard 등)는 MSYS 경로 변환으로 망가진다. `MSYS_NO_PATHCONV=1`을 설정한다.
- Windows PowerShell 5.1은 BOM 없는 UTF-8 .ps1과 `Get-Content`의 한글을 CP949로 읽는다. 한글이 든 .ps1은 UTF-8 BOM으로 저장한다.

Pleos SDK 함정 (이전 세션 실측, 구현 전에 문서로 다시 확인할 것):
- Nexus에는 Vehicle 2.3.1이 없다. 2.3.2를 쓴다. Pleos 이미지는 `cmdline-tools\19.0`의 sdkmanager로 설치한다.
- 속도·기어·외기온도·연료 레벨은 Vehicle SDK가 아니라 AAOS Car API(`useLibrary("android.car")`)로 얻는다는 'SDK API 정책' 문서가 있었다. `CAR_SPEED`는 런타임 권한이다.
- 영역(area)은 항상 `check*Capabilities`로 먼저 얻는다. 에뮬레이터의 Vehicle SDK 권한은 모두 normal 수준이다.
- Pleos LLM SDK는 2026-10-15에 제공이 종료됐다.

관련: [[drivelog-project]]

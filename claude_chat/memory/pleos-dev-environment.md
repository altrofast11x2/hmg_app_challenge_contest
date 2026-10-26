---
name: pleos-dev-environment
description: "이 PC의 Pleos Connect 개발환경과 함정 (JAVA_HOME이 JBR 21, Vehicle 2.3.1 없음, adb는 user 10, MSYS 경로 변환 등)"
metadata:
  node_type: memory
  type: reference
  originSessionId: a0acef44-095f-4903-afed-69862aa322b3
  modified: 2026-10-26T03:13:29.793Z
---

2026-10-22~24에 이전 폴더(C:\dev\hmg_app_challenge_contest)에서 검증하고 10-26에 DriveLog에서 다시 확인한 구성:
- JDK 17: `%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-17.0.20.1+1` (사용자 환경변수 JAVA_HOME). 그런데 Claude Code 세션 프로세스의 JAVA_HOME과 PATH의 java는 `~/.jdks/jbr-21.0.11`이다. gradlew를 실행할 때는 JAVA_HOME을 JDK 17로 직접 지정한다.
- Gradle 8.10.2 공식 배포판이 `~/.gradle/wrapper/dists/gradle-8.10.2-bin/a04bxjujx95o3nb99gddekhwo/`에 있다 (zip sha256 = 공식 31c55713…). 시스템 gradle은 없다.
- AVD `Pleos_Connect_v2.1`: 이미지 `system-images;android-34;pleos_car_emulator;x86_64`, cold boot, RAM 4GB. 화면 2560x1440이고, 앱은 홈 화면의 오른쪽 영역(왼쪽은 시스템 DrivingView)에 뜬다.
- AAOS 운전자 사용자 ID는 10이다. 런타임 권한은 `pm grant --user 10 ...`으로 준다. 에뮬레이터는 user 빌드라 `adb root`, VHAL dump, `cmd car_service` 디버그 명령을 쓸 수 없다.
- Virtual Console: `adb forward tcp:8088 tcp:8088` 후 http://127.0.0.1:8088/
- Git Bash에서 adb 경로(/sdcard 등)는 MSYS 경로 변환으로 망가진다. `MSYS_NO_PATHCONV=1`을 설정한다.
- Windows PowerShell 5.1은 BOM 없는 UTF-8 .ps1과 `Get-Content`의 한글을 CP949로 읽는다. 한글이 든 .ps1은 UTF-8 BOM으로 저장한다.

2026-10-26 2단계 조사에서 확인:
- AVD에 GMS·Play 스토어·GSF가 없다 → Firebase 대신 순수 HTTPS인 Supabase를 제안했다.
- 부팅 직후 Wi-Fi(AndroidWifi, 10.0.2.16)는 연결돼 있는데 "Active default network: none"이라 인터넷이 안 됐다. `adb shell cmd wifi set-wifi-enabled disabled` → `enabled`로 복구된다. eth0는 VHAL용이다.
- 공식 문서에는 시그니처가 거의 없다. 정확한 시그니처는 소스 jar에서 확인한다: Vehicle은 Gradle 캐시(`~/.gradle/caches/modules-2/files-2.1/ai.pleos.playground/Vehicle/2.3.2/*/Vehicle-2.3.2-sources.jar`), NaviHelper·navi-data는 Nexus에서 `-sources.jar`로 받는다. 정리본은 저장소 `docs/sdk-usage.md`에 있다.
- Vehicle `Gear` API는 deprecated("use the Google GEAR_SELECTION property")다 → AAOS CarPropertyManager GEAR_SELECTION(android.car.permission.CAR_POWERTRAIN, normal)을 쓴다. 계획한 pleos 권한(CAR_DRIVE/INFO/ENERGY/SAFETY/SEATS, NAVI_ROUTE)은 모두 normal이다.
- Virtual Console에서 바꿀 수 있는 것: 기어 P/R/N/D, Odometer(1km 단위), 좌석 착석, Telltales, EV 배터리 등. 바꿀 수 없는 것: 속도, 시동, GPS 위치, 내비 경로, 좌석 위치.
- NaviHelper `get*Info()`는 Unit을 반환하고 결과는 `NaviHelperEventListener.on*Info()`로 온다. `CurrentLocationInfo`에 address·lat/lng가 있다.

Pleos SDK 함정 (이전 세션 실측, 구현 전에 문서로 다시 확인할 것):
- Nexus에는 Vehicle 2.3.1이 없다. 2.3.2를 쓴다. Pleos 이미지는 `cmdline-tools\19.0`의 sdkmanager로 설치한다.
- 속도·기어·외기온도·연료 레벨은 Vehicle SDK가 아니라 AAOS Car API(`useLibrary("android.car")`)로 얻는다는 'SDK API 정책' 문서가 있었다. `CAR_SPEED`는 런타임 권한이다.
- 영역(area)은 항상 `check*Capabilities`로 먼저 얻는다. 에뮬레이터의 Vehicle SDK 권한은 모두 normal 수준이다.
- Pleos LLM SDK는 2026-10-15에 제공이 종료됐다.

관련: [[drivelog-project]]

# 프로젝트 명세 (사용자 원문)

> 2026-10-26 세션(`a0acef44-095f-4903-afed-69862aa322b3`)의 첫 메시지를 그대로 옮긴 것이다. 이후 세션은 이 문서를 기준으로 진행한다.
> 원문 뒤에 붙은 **추가 지시**가 원문과 다르면 추가 지시를 따른다.

---

# 프로젝트: 자동 운행기록부 (현대자동차그룹 차량용 앱 개발 공모전 출품작, Pleos Connect 앱)

오늘은 2026-10-25다. 팀명(개발사명)은 AltroStudio, 앱 영문명은 DriveLog(임시)다. 작업 폴더는 C:\Users\kazin\AndroidStudioProjects\DriveLog이고 지금은 비어 있다. 이 프로젝트는 공모전 심사·운영 규정에 맞춰 개발한다. 아래 규칙이 기술적 취향보다 우선한다.

## 0. 진행 방식
- 나는 Android/Kotlin이 처음이야 (JS는 공부해 봤어). 단계마다 무엇을 했는지, 그리고 내가 직접 해야 할 일(에뮬레이터 실행, Virtual Console 조작 등)을 한국어로 짧게 알려줘.
- 단계가 끝날 때마다 멈추고 보고해. 내가 확인하면 다음으로 넘어가.
- 큰 결정(라이브러리, 백엔드, 구조 변경)은 먼저 물어봐.
- Pleos SDK의 클래스명과 메서드 시그니처는 추측하지 말고 아래 문서를 WebFetch로 읽어서 확인해.
- git init 후 단계마다 커밋해. 비밀값(keystore, 비밀번호, API 키)은 절대 커밋하지 말고 .env.example 같은 예시 파일만 둬.
- 외부로 나가는 작업(GitHub 저장소 생성, push, 서버 배포)은 실행 전에 나에게 확인받아.

## 1. 이미 확인된 내 개발 환경 (다시 설치하지 마)
- Android SDK: C:\Users\kazin\AppData\Local\Android\Sdk (platforms android-34, android-35)
- Pleos Connect 에뮬레이터 이미지: system-images\android-34\pleos_car_emulator\x86_64
- AVD: Pleos_Connect_v2.1 (Automotive, cold boot, RAM 4GB)
- JDK 17 (Eclipse Adoptium, PATH에 있음), Android Studio 번들 JDK: C:\Program Files\Android\Android Studio\jbr
- Node.js 22, git, GitHub CLI(gh, altrofast11x2 계정으로 로그인됨), Claude Code
- Android 프로젝트는 아직 없다 → 10번 진행 순서의 1단계에서 만든다.

## 2. 공모전 일정과 범위
- 1차 서류 마감은 11/15(일)이다. 필수 제출물은 기획서(접수양식, 이메일 제출)이고, 시연 영상, GitHub, APK는 가산점이다.
  → M1: 11/13까지 완성 (이틀 버퍼)
- 11/23에 1차 결과가 발표되고(20팀), 12/3~4에 2차 PT를 한다. PT에서는 실제로 동작하는 앱 시연이 필수다(에뮬레이터 화면 가능).
  → M2: 11/23~12/2
- 12/18 최종 발표 이후는 전시 준비 기간이다. 이때 추가 개발해도 순위에 영향이 없으니 12/3 전에 기능을 완성한다.
- M1 주간 목표:
  - 11/1: 운행 감지와 로컬 저장
  - 11/8: 분류, 기록부, 계정 최소판, 동기화
  - 11/13: 제출물 완성
- M1에서 M2 기능을 만들지 마. 대신 데이터 모델과 구조는 M2를 그대로 받을 수 있게 설계해.

## 3. 심사 기준과 구현 매핑 (모든 기능은 이 표에 연결돼야 한다)
| 심사 기준 | 이 앱에서 증명할 것 | 구현과 증거물 |
|---|---|---|
| 차량특화성: 모바일 대비 차량만의 가치, 주행·정차 맥락과 차량 기능의 결합 | 차가 직접 쓰는 기록. 계기판 거리, 차량 식별(VIN), 주행 상태에 따라 바뀌는 UI | Odometer 누적거리로 주행 전/후 계기판 거리를 자동 기입. 주행 중에는 조작 0, 정차 시 분류 |
| 창의성: 독창성, 운전자·동승자의 불편과 해결 방식 | 운전자(작성이 귀찮음), 관리자(취합·검증이 어려움), 세무 담당(증빙을 믿기 어려움)을 한 번에 해결. "고칠 수 없는 기록" | 원본 보존, 수정 이력, 차량별 해시 체인으로 변조를 감지 |
| 기술 활용성: Pleos SDK·API를 핵심 기능에 활용 | SDK 없이는 앱이 성립하지 않는 구조 | docs/sdk-usage.md에 기능별 사용 API 매핑표 유지 (기획서와 PT에 그대로 사용) |
| 사업성: 타깃, 예상 MAU, Retention, 확대 계획 | 기업 하나를 들이면 차량 수만큼 사용자. 월말 정산 때문에 매달 재사용 | docs/business.md 틀만 만들고, 숫자는 지어내지 말고 출처 칸을 비워 둬 (내가 공식 통계로 채움) |
| 가산점: 시연 영상, GitHub(소스와 APK) | 1차 때 셋 다 제출 | apk/ 폴더, README 필수 항목, docs/demo-script.md |

## 4. 서비스와 계정 구조
한 줄 소개: 외근이 잦은 법인·개인사업자를 위해 차가 알아서 운행기록부를 써 주는 앱. 주행 중에는 조작이 없고, 정차 후 한 번 눌러 용도만 고르면 월별 운행기록부가 완성된다.

계정 4종:
1. 차량 (M1): 사람 계정이 아니다. 관리자가 발급한 6자리 연결 코드를 정차 중에 한 번 입력하면 VIN과 함께 조직에 등록된다. 차 안에서 비밀번호를 입력하는 흐름은 만들지 마.
2. 운전자 (M1: 기본 1명, 관리자가 추가 가능 / M2: 정차 중 목록에서 선택, 좌석 위치로 지난번 운전자 자동 추천)
3. 관리자 (M1 최소판): 기업 담당자, 렌터카·리스사. 차량 등록, 연결 코드 발급, 운행 열람 (M2: 운전자 배정, 월말 마감 승인)
4. 열람자 (M2): 회계·세무사. 읽기와 다운로드만 가능

만들지 않을 것: 실시간 위치 추적 (직원 감시 논란 방지).

## 5. SDK (Pleos 공식 문서에서 확인된 값)
- minSdk 28 이상 필수 (차량 보안 요건). compileSdk/targetSdk 35, JDK 17, Kotlin. Sample App 기준: Gradle 8.10.2, AGP 8.8.2, Kotlin 2.0.0
- 실행 환경: Pleos Connect Emulator AVD. 일반 폰 에뮬레이터에는 Vehicle Service가 없다.
- Maven 저장소 (settings.gradle.kts의 dependencyResolutionManagement.repositories): https://nexus-playground.pleos.ai/repository/maven-releases/
- 의존성: ai.pleos.playground:Vehicle:2.3.2, ai.pleos.playground:NaviHelper:3.0.5
- 지금 쓸 수 있는 건 Vehicle State(조회)와 NaviHelper뿐이다. FusedLocation, TTS, STT, Vehicle Control은 준비 중이라 쓰지 마.

Vehicle SDK
- Vehicle 진입점: initialize() → getXxx()로 도메인 객체 획득 → 끝나면 release(). 모든 호출은 IPC 비동기(onComplete/onFailed)다. 콜백이 끝나기 전에 release()하지 말 것.
- 쓰기 전에 checkXXXCapabilities로 지원 여부를 확인한다.
- 사용할 값:
  - Odometer: current-distance-driven(누적거리 = 계기판 거리), trip-current-driving-distance/time
  - CarInfo: VIN
  - Seat: 앞뒤 위치, 높이, 등받이 각도, 탑승 여부 (M2)
  - Fuel/EvBattery: 연비·전비, 잔량
  - Safety: telltales(주차 브레이크 등)
- 퍼미션은 필요한 것만 선언: pleos.car.permission.CAR_DRIVE, CAR_INFO, CAR_ENERGY, CAR_SAFETY, CAR_SEATS(M2). VIN 조회에 필요한 퍼미션은 문서에서 다시 확인.

NaviHelper SDK
- 흐름: 생성 → initialize() → addListener() → 조회 → removeListener()/release()
- 사용할 것: getCurrentLocationInfo()(주소), getDestinationInfo(), getWaypointInfo(), onDestinationArrived, onWayPointArrived, onRouteStarted
- 퍼미션: pleos.car.permission.NAVI_ROUTE (조회 전용이면 이것만)
- 내비 경로 없이 주행하는 경우 주소를 어떻게 얻을지 확인하고 대안을 제안해.

주행 상태 판단: Vehicle SDK 조회 목록에 기어 값이 없다. AAOS 표준 CarUxRestrictionsManager 등 실제로 쓸 수 있는 방법을 확인해서 제안해. Virtual Console에는 Gear(P/R/N/D), Odometer(1km 단위), Seat Occupancy, Telltales 카드가 있다.

## 6. 기능 명세
### M1: 차량 앱
1. 첫 실행 동의 화면 (정차 중): 수집 항목, 목적, 보유기간, 거부할 권리를 보여 준다. 동의 버튼을 누른 것만 동의로 처리한다(뒤로가기나 빈 곳 터치는 동의가 아님). 동의를 철회하면 저장된 데이터를 즉시 삭제한다.
2. 운행 자동 감지 (상태 머신: 대기 → 주행 중 → 정차 → 종료)
   - 시작 시 시각, 시작 계기판 거리, 시작 주소를 기록한다.
   - 주행 중에는 경유지와 목적지 도착 이벤트를 기록한다.
   - 종료 시 시각, 종료 계기판 거리, 종료 주소, 연비·전비를 기록하고 "미분류" 상태로 둔다.
   - 시동이 꺼지면 앱이 갑자기 죽을 수 있으니 상태가 바뀔 때마다 즉시 DB에 저장한다.
3. 주행 중 화면: "운행 기록 중"과 최소 정보만 보여 준다. 터치할 요소는 없다.
4. 정차 후 원탭 분류: 출퇴근 / 일반업무 / 비업무. 같은 목적지는 지난번 분류를 기본값으로 제안한다.
   - 비업무 운행은 주소·위치를 저장하지 않고 거리만 남긴다.
5. 월별 운행기록부 화면 (국세청 '업무용승용차 운행기록부' 서식 열):
   - 사용일자, 사용자, 주행 전/후 계기판 거리, 주행거리, 업무용 사용거리(출퇴근용 / 일반업무용), 비고(방문처)
   - 월 요약: 총 주행거리, 업무용 사용거리, 업무사용비율. 서식이 최신인지는 내가 따로 확인한다.
6. 무결성
   - 계기판 값은 출처를 VEHICLE로 표시한다. 사람이 수정하면 원본을 보존하고 수정 이력을 남긴다.
   - 차량별 해시 체인: 각 기록에 SHA-256(필드 + 이전 해시)을 저장한다.
7. CSV 내보내기 (Excel 한글 깨짐 방지용 UTF-8 BOM). 앱 안에 "오픈소스 라이선스" 화면을 둔다.

### M1: 계정 최소판
8. 백엔드
   - 2단계에서 AVD에 Google Play Services가 있는지 adb로 확인하고, 그 결과로 Supabase(Postgres + Auth + RLS)와 Firebase 중 하나를 제안해. GMS가 없을 가능성이 있어서 Supabase를 우선 검토한다.
   - 역할은 관리자와 운전자만 구현한다. 권한은 RLS 정책으로 강제하고, 열람자를 바로 추가할 수 있는 구조로 만든다.
   - service_role 키는 절대 APK나 웹에 넣지 마.
9. 차량 연결: 관리자 웹에서 6자리 코드 발급 → 정차 중 차에서 입력 → VIN과 함께 조직에 등록
10. 동기화: 오프라인 우선. 로컬 Room에 먼저 저장하고, 연결되면 업로드한다(재시도, 중복 방지).
11. web/ 관리자 최소판 (JS): 로그인, 차량 목록, 연결 코드 발급, 운행 목록과 월별 기록부 보기, CSV 다운로드
12. 시연용 데모 조직과 계정은 backend/seed에만 둔다. APK 안에는 더미 데이터 금지.

### M2 (내가 시작하라고 할 때만)
13. 열람자 역할, 월말 마감 승인, 운전자 배정
14. 운전자 여러 명 선택과 좌석 위치 기반 추천 (Virtual Console에서 좌석 위치를 흉내 낼 수 없으면 알려줘)
15. 웹에 무결성 확인 배지 (해시 체인 검증)
16. PT 시연 리허설 체크리스트 (에뮬레이터와 웹을 나란히 띄우고 한 흐름으로 시연)

## 7. 데이터 모델과 구조 원칙 (M1부터 적용)
- Kotlin + Jetpack Compose, MVVM, Room, Coroutines/Flow
- 모든 운행 기록에 orgId, vehicleId(VIN 기반), driverId, 출처, 수정이력, prevHash/hash를 넣는다.
- Vehicle과 NaviHelper는 인터페이스(VehicleDataSource, NaviDataSource) 뒤에 둔다. 실제 구현과 Fake 구현을 만들고, Fake는 debug 빌드에만 넣는다.
- 운행 감지 상태 머신과 해시 체인은 단위 테스트를 작성한다.
- 저장소 계층과 동기화(SyncRepository)를 분리해서 백엔드를 바꿔도 앱 로직이 흔들리지 않게 한다.

## 8. 반드시 지킬 규칙
Pleos 앱 심사
- 주의 분산: 주행 중에는 터치할 일이 없어야 하고, 조작은 정차 중에만 허용한다.
- 성능: 실행과 로드는 10초 이내, 버튼 반응은 2초 이내(넘으면 로딩 표시), 터치 피드백은 0.25초 이내
- 백그라운드에 갔다 돌아오면 상태를 복원한다. 차량 가로 디스플레이에 꽉 차게(레터박스 없이), 큰 터치 영역
- 개인정보: 권한은 최소로. 수집·이용·공유를 앱 안에서 명확히 고지한다. 개인정보 처리방침 화면을 둔다. 운전자가 자기 기록을 열람할 수 있게 한다. 위치정보 관련 등록·신고 이슈는 docs/business.md의 리스크 항목에 적어 둔다.

공모전 운영 규정
- 순수 창작물이어야 한다. 남의 코드나 디자인을 베끼지 말고, 쓰는 오픈소스는 라이선스를 확인해 앱 안과 README에 고지한다. 폰트, 아이콘, 음원은 상업 이용 가능한 것만 쓴다. 현대·Pleos 로고나 상표는 쓰지 마.
- 제출 내용이 사실과 다르면 선정이 취소된다. docs/status.md에 기능별로 [완료 / 진행 중 / 계획]을 정직하게 유지하고, README와 기획서에 미구현 기능을 완료처럼 쓰지 마.

## 9. 저장소와 제출물
- 구조: app/ (차량 앱), web/ (관리자 웹), backend/ (스키마, RLS, seed), docs/, apk/, README.md
- GitHub
  - 내 개인 계정(altrofast11x2)에 비공개 저장소 DriveLog로 만든다. Organization은 쓰지 않는다. gh는 이미 로그인돼 있다.
  - 저장소 생성(gh repo create)과 push는 실행 전에 확인받아.
  - .gitignore에 keystore, local.properties, .env, 빌드 산출물을 넣는다. 첫 push 전과 제출 전에 비밀값이 커밋에 섞였는지 검사하고 결과를 보고해.
  - 제출 직전에 나에게 물어볼 것: 개인 계정의 비공개 저장소는 심사용 GitHub 계정을 Collaborator로 초대하면 쓰기 권한까지 주게 된다(공모전 안내). (a) 비공개 그대로 초대할지, (b) 마감 직전에 공개로 바꿀지 결정해야 한다.
- APK: 실행 가능한 release 서명 빌드를 apk/AltroStudio_DriveLog_release.apk로 둔다 (형식: 팀명_앱이름_release.apk). 100MB가 넘으면 GitHub Releases에 올린다.
- README.md 필수 항목: 앱 이름과 한 줄 소개, 실행 방법, 테스트 환경과 필요한 설정(AVD, Virtual Console, 데모 계정 사용법), APK 위치 또는 Release 링크
- docs/
  - sdk-usage.md: 기능별 SDK API 매핑
  - status.md: 구현 현황
  - demo-script.md: 시연 시나리오
  - business.md: 타깃, MAU와 Retention 계산 틀, 확대 계획(법인 → 렌터카·리스 → 공유차·보험), 리스크. 숫자는 비워 둘 것

## 10. 시연 영상과 숏폼
- docs/demo-script.md: 2~3분 기준 장면별 표
  - 열: 시간, 화면(에뮬레이터 / Virtual Console / 관리자 웹), 조작, 자막 문구
  - 흐름: 문제 제기 → 관리자 웹에서 연결 코드 발급, 차에서 입력 → Virtual Console 주행(주행 중 조작 0 강조) → 정차 후 원탭 분류 → 웹 반영, 월별 기록부, CSV → SDK 활용 요약
- 촬영 전 체크리스트: AVD cold boot, release 빌드 새로 설치, 데모 계정 준비, 알림 끄기, 한국어 UI
- 녹화는 내가 OBS로 한다. 에뮬레이터 화면만 따로 녹화할 때 쓸 adb shell screenrecord 명령(3분 제한)을 README의 개발자 메모에 적어 둔다.
- 숏폼용(30초~1분 세로 영상)으로 잘라 쓸 장면 3개(Spot: 불편한 순간 / Solve: 해결 화면 / Scene: 사용 장면)를 demo-script.md 끝에 표시한다.

## 11. 진행 순서 (단계마다 멈추고 보고)
M1
1. 프로젝트 생성 (작업 폴더에)
   - Android Studio의 Empty Activity(Compose) 템플릿과 같은 구조로 만든다: settings.gradle.kts, 루트·app build.gradle.kts, gradle/libs.versions.toml
   - 설정: 패키지 com.altrostudio.drivelog, 앱 이름 "자동 운행기록부", minSdk 28, compileSdk/targetSdk 35, AGP 8.8.2, Kotlin 2.0.0
   - Gradle wrapper 8.10.2를 gradle-wrapper.jar까지 포함해서 넣는다. 시스템에 gradle이 없으면 공식 배포판을 받아 gradle wrapper로 생성한다.
   - local.properties에 sdk.dir를 넣고, .gitignore를 만들고, git init 후 첫 커밋
   - gradlew assembleDebug 성공 → Pleos_Connect_v2.1 AVD를 켜서 설치·실행까지 확인
   - 끝나면 멈추고 "Android Studio에서 File > Open으로 이 폴더를 열고, Gradle 동기화가 끝나면 Terminal에서 claude --continue로 돌아오라"고 안내해 줘.
2. 환경 점검: AVD의 GMS 유무(adb), 아래 문서 읽기 → 구현 계획과 백엔드 제안 보고
3. Pleos Maven 저장소와 의존성, 퍼미션 설정, debug 전용 SDK 연결 확인 화면 → 내가 Virtual Console에서 Odometer와 기어를 바꿔 확인
4. 데이터 모델(Room, 7번 원칙), 운행 감지 상태 머신, 해시 체인, 단위 테스트 (목표 11/1)
5. 동의 화면, 주행 중 화면, 정차 후 분류, 월별 기록부, CSV, 오픈소스 라이선스 화면
6. 백엔드 스키마와 RLS(관리자, 운전자), 연결 코드, 동기화
7. web/ 관리자 최소판 (목표 11/8)
8. GitHub 저장소(확인 후 생성·push), release APK, README, docs 4종
9. 제출 직전 점검표 보고 (목표 11/13)
M2: 6번의 M2 항목을 내가 시작하라고 할 때 진행

## 12. 제출 직전 점검표 (9단계에서 표로 보고)
- [ ] apk/AltroStudio_DriveLog_release.apk가 새 AVD에 설치해서 실행되는 release 서명 빌드인지
- [ ] README 필수 4항목
- [ ] docs/status.md에 미구현 기능을 완료로 적지 않았는지
- [ ] 오픈소스 라이선스 고지 (앱 안과 README)
- [ ] 비밀값 미포함
- [ ] GitHub 공개 범위 결정: 비공개 그대로 심사용 계정 초대(쓰기 권한 부여) 또는 공개 전환. 초대한 경우 접근 확인
- [ ] 기획서에 옮길 요약: 심사 4항목별 핵심 문장, SDK 활용 매핑표, 구현 현황

## 13. 참고 문서 (구현 전에 직접 읽기)
- 에뮬레이터 설치: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- Vehicle SDK: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro
- Vehicle 설치: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup
- Vehicle 퍼미션: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission
- Vehicle Sample App: https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app
- NaviHelper: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro
- NaviHelper 설치: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk
- NaviHelper 퍼미션: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions
- Virtual Console: https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide
- 주행 규제 정책: https://document.pleos.ai/docs/pleos-only/infolink-emulator/driving-restiction-policy
- 심사: 주의 분산: https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-driver-distraction-elements
- 심사: 성능·안정성: https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-functionality-performance-stability
- 심사: 개인정보·보안: https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-user-privacy-security
- 공모전 심사·시상: https://appdevchallenge.auto-hmg.io/guide/judging-awards
- 공모전 제출 방법: https://appdevchallenge.auto-hmg.io/guide/submission
- 출품 유의사항: https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000006

1단계부터 시작해 줘.

---

## 추가 지시 (2026-10-26, 같은 세션)

- 저장소: 9번의 "비공개 저장소 DriveLog 새로 만들기" 대신 기존 비공개 저장소 `altrofast11x2/hmg_app_challenge_contest`의 **`altrofast11x2` 브랜치**에서 작업한다.
- 백업이 필요하면 `altrofast11x2_backup` 브랜치를 만든다. → 2026-10-26에 기존 내용(`5f8b4cb`)을 백업했다.
- `altrofast11x2` 브랜치에 있던 기존 내용(이전 아이디어 탐색용 프로젝트, 이전 대화 기록)은 모두 지워도 된다.
- Claude 대화 기록은 `claude_chat/` 폴더에 넣는다.

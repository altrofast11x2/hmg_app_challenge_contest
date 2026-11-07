# Pleos App Market 제출 준비

> Pleos 「앱 심사 제출」·「Pleos App Market 등록정보」·「앱 버전 관리 정책」·「앱 태그 표기 가이드」·「앱 업데이트 가이드」에 맞춘 제출 정보 초안입니다 (2026-10-28).
> 공모전 제출과는 별개로, App Market에 올릴 때 이 표를 그대로 옮겨 적습니다. 가이드 대조 결과는 [pleos-guide-check.md](pleos-guide-check.md).

## 앱 식별·기본 정보

| 항목 | 값 |
|---|---|
| 앱 관리명 (마켓에 안 보임) | DriveLog |
| 버전명 / 버전 코드 | 0.2.0 / 2 (`app/build.gradle.kts`) |
| 국가/지역 | 대한민국 (국세청 업무용승용차 운행기록부 서식) |
| 패키지 파일 | `apk/AltroStudio_Chagok_release.apk` (약 53 MB, 한도 600 MB) |
| 대상 플랫폼 | Connect |
| 카테고리 | Business (업무 및 생산성 지원) |
| 연령 등급 | 전체 이용가 예정. IARC 등급 분류를 받아 증적을 함께 제출 (남음) |
| 개인정보 처리 | 수집: 운행 시작·종료 시각, 계기판 거리, 배터리 잔량, 출발·도착 주소, 방문처(내비 도착 정보), VIN 또는 기기 ID, 운행 용도 · 이용: 운행기록부 작성 · 전송: 조직에 연결한 차량만 조직 서버로 · 파기: 비업무는 주소 즉시 삭제, 동의 철회 시 차 안 전부 삭제 |
| 위치정보 활용 | 활용함 — 운행 시작·종료 두 시점의 주소만 (NaviHelper). 실시간 위치 추적 없음, GPS·FusedLocation 미사용 |
| 앱 접근 권한 | 차량 데이터: `CAR_DRIVE`(계기판), `CAR_INFO`(VIN), `CAR_ENERGY`(배터리·전비), `NAVI_ROUTE`(주소·도착), AAOS `CAR_POWERTRAIN`(기어·주차 브레이크) · 음성 안내 `TTS_SERVICE` · 인터넷 · 포그라운드 서비스 · 부팅 완료. 카메라·마이크·연락처·저장소 권한 없음 |
| SDK 추가 권한 | Pleos Playground My Project "자동 운행기록부"의 프로젝트 ID `d2e82bc9-88ab-43b4-90a4-7fc6fa4975ef` (카테고리 비즈니스). 2026-10-28 확인한 SDK 권한 상태: Vehicle 조회 11개 항목·NaviHelper 2개 항목 **사용 가능**, TextToSpeech·SpeechToText·FusedLocation·차량 제어는 **준비 중**(신청 버튼 없음). 이 앱은 TextToSpeech를 차량 안 엔진(ON_DEVICE)으로만 써서 서버 등록이 필요 없지만, 마켓 심사에서 TTS 권한 상태를 묻는다면 partnership@pleos.ai에 확인. 공모전 설명회(2026-10-28): 개발 단계에서는 승인 없이 manifest 선언만으로 Vehicle·NaviHelper·STT·TTS·FusedLocation을 쓸 수 있고, 승인된 권한만 심사하는 것은 마켓 배포 때. **0.3.0 추가**: 차량 제어 `CAR_CLIMATE`·`CONTROL_CAR_CLIMATE`·`CAR_SEATS`·`CONTROL_CAR_SEATS`·`CAR_LIGHTS`·`CONTROL_CAR_LIGHTS`·`CAR_WINDOWS`·`CONTROL_CAR_WINDOWS`·`CAR_DOORS`·`CONTROL_CAR_DOORS` (Playground 문서상 Vehicle Control SDK는 "준비 중" → App Market에 올릴 때 신청·승인 필요. 팀은 아직 법인이 없어 App Market 등록은 법인 설립 뒤로 미룸, 2026-11-04. 공모전 제출·시연에는 필요 없음), Gleo Interface `assets/schema.json`(Pleos 스키마 검토 뒤 활성화, 신청 준비: [gleo-schema-request.md](gleo-schema-request.md)) |
| 광고성 정보 | 보내지 않음 |

## 언어별 정보 (한국어)

| 항목 | 값 |
|---|---|
| 앱 이름 | 자동 운행기록부 (영문 공식명 DriveLog) |
| 간단한 설명 | 기어와 계기판으로 쓰는 업무용 차량 운행기록부 |
| 자세한 설명 | 운행을 시작하면 차가 계기판 거리와 출발·도착지를 자동으로 적고, 정차하면 출퇴근·일반업무·비업무 중 하나만 누르면 됩니다. 주행 중에는 화면을 만지지 않아도 운행 시작과 정차를 음성으로 알려 줍니다. 국세청 업무용승용차 운행기록부 서식 그대로 월별 기록부를 만들고, 기록은 해시 체인으로 묶여 고친 흔적이 남습니다. 회사에 연결하면 관리자가 웹에서 모든 차량의 기록부를 받아 세무 제출에 씁니다. 비업무 운행은 주소 없이 거리만 남습니다. |
| 태그 (최대 20) | `DriveLog`(영문 공식명, 필수), 자동 운행기록부, 운행기록부, 업무용 차량, 법인차, 국세청, 차량일지 |
| 개인정보처리방침 URL | https://altrostudio-drivelog.vercel.app/privacy.html (앱의 처리방침 문구로 웹 빌드 때 만든다: `web/scripts/privacy.mjs`) |
| 위치정보 서비스 이용약관 URL | **남음** — 위치정보 활용을 "활용함"으로 고르면 필수. 초안: [location-terms-draft.md](location-terms-draft.md). 위치기반서비스사업 신고 대상인지 법률 검토 뒤 공개(웹 `/location-terms.html`) |
| 앱 아이콘 | [app-market-assets/icon-512.png](app-market-assets/icon-512.png) (512×512 PNG, 9 KB). 앱의 adaptive icon(배경 #0B3D5C + 장부·체크 벡터)에서 기기에 보이는 가운데 72dp를 그린 것 (`make_icon.py`) |
| IVI 스크린샷 | [app-market-assets/](app-market-assets/) `screenshot-1-classify` ~ `screenshot-5-settings` (1700×1212 PNG 5장: 정차 후 원탭 분류, 주행 중, 홈, 월별 운행기록부, 설정·음성 안내). 에뮬레이터 앱 창(1682×1210)을 그대로 잘라 가운데에 둠 (`make_screenshots.py`). 지금 데모 데이터에는 콜드 부팅 때 생긴 0→255 km 시험 운행이 섞여 업무사용비율이 23.3%로 보인다 → 마켓용으로는 깨끗한 데모 데이터로 다시 찍는 것을 권장 |
| 출시 노트 | 0.2.0: 음성 안내, 차 안 기록 암호화, 새 버전 안내, 뒤로 가기 시 앱 숨김 |
| 고객 지원 | 이메일 kazinobel9807@gmail.com · 웹사이트 https://altrostudio-drivelog.vercel.app |

## App Market Console

Playground 상단 Workspace › Pleos App Market Console (https://appmarket.pleos.ai/console) 에서 앱을 추가하고 심사를 요청한다. 현대자동차그룹 통합 계정(account.hyundaimotorgroup.com)으로 로그인해야 하고, 처음이면 콘솔 개발자 가입·약관 동의가 필요하다 — 본인이 직접 한다. IARC 연령 등급도 콘솔의 등록 과정에서 받는다.

**2026-10-28 확인한 가입 조건** (https://appmarket.pleos.ai/console/join): ① 비즈니스 유형 선택 → ② 조직 인증 정보 입력 → ③ 인증 심사. 유형은 **법인만** 가능하고 "로그인한 계정 ID의 도메인과 개발사 웹사이트의 도메인이 일치"해야 한다. **개인은 "향후 지원 예정"**이라 지금은 고를 수 없다. 공모전(1차 서류·2차 PT·전시)은 App Market 등록을 요구하지 않으므로(참가 가이드·FAQ에 언급 없음), 지금은 이 문서의 자료를 준비해 두고 법인·도메인 메일이 생기거나 개인 가입이 열리면 등록한다. 궁금하면 appdevchallenge@hyundai-partners.com(공모전), partnership@pleos.ai(Pleos)에 문의.

## 심사 노트 (초안)

- 시험 방법: Pleos Connect Emulator + Virtual Console에서 기어 D → 계기판 거리 증가 → P → 분류 화면에서 용도 선택. 주행 중 화면에는 터치 요소가 없습니다.
- 조직 연결 시험: 연결 코드 `422511` (데모 기업, 2027-02-28까지). 연결하지 않아도 [나중에 연결]로 모든 차량 기능을 쓸 수 있습니다.
- 관리자 웹 계정: 데모 기업 관리자 계정(실제 사용자 기준)을 심사 요청 때 따로 전달합니다. 비밀번호는 저장소에 두지 않습니다.
- 음성 안내는 차량 안 음성 엔진(ON_DEVICE)만 쓰므로 서버 등록이 필요 없습니다.

## 버전 관리 (Pleos 앱 버전 관리 정책)

앱에 영향을 주는 변경(기능, 버그 수정, 설정)을 배포하거나 심사를 요청할 때마다:

1. `app/build.gradle.kts`의 `versionCode`를 1 올리고 `versionName`을 바꾼다.
2. release APK를 만들어 `apk/`에 넣고 심사를 요청한다.
3. 심사를 통과해 마켓에 올라가면 `web/app-version.json`을 같은 번호로 바꿔 관리자 웹을 배포한다. 차량 앱은 하루 한 번 이 파일을 보고, 새 버전이면 정차 중 홈에서 "최신 버전이 있습니다. 업데이트를 위해 앱 마켓 페이지로 이동할까요?"를 띄운 뒤 `market://detail?package=com.altrostudio.drivelog`로 보낸다 (Pleos 앱 업데이트 가이드).

마켓에 올라가기 전에 `app-version.json`을 먼저 올리면, 아직 받을 수 없는 버전을 안내하게 되니 순서를 지킨다.

| 버전 | 코드 | 날짜 | 내용 |
|---|---|---|---|
| 0.1.0 | 1 | ~2026-10-27 | 첫 개발 버전 (M1 운행 기록, 조직 연결, 관리자 웹) |
| 0.2.0 | 2 | 2026-10-28 | 음성 안내, Pleos 가이드 대조 수정(암호화, 뒤로 가기, 오디오 포커스, 업데이트 안내 등) |

# 기획서 요약 (접수양식에 옮길 내용)

> 2026-10-26 기준. 접수양식(기획서)에 그대로 옮기거나 줄여 쓸 수 있게 정리했다. 구현 여부는 [status.md](status.md)와 같고, 확인하지 않은 기능은 "계획"이나 "진행 중"으로 적었다. 사업성 숫자는 [business.md](business.md)에 팀이 공식 통계로 채운다.

## 앱 이름과 한 줄 소개

- **앱 이름**: 자동 운행기록부 (영문명 DriveLog, 임시)
- **한 줄 소개**: 외근이 잦은 법인·개인사업자를 위해 차가 알아서 운행기록부를 써 주는 앱. 주행 중에는 조작이 없고, 정차 후 한 번 눌러 용도만 고르면 월별 운행기록부가 완성된다.

## 해결하려는 불편

| 누구 | 지금의 불편 | 이 앱에서 |
|---|---|---|
| 운전자 | 운행마다 계기판 거리를 보고 적어야 하고, 몰아서 쓰게 된다 | 거리·시각·주소를 차가 직접 기록. 정차 후 용도만 한 번 누른다 |
| 차량 관리자 | 운전자별 기록부를 매달 걷고 확인해야 한다 | 관리자 웹에서 모든 차량의 월별 운행기록부와 CSV를 바로 본다 |
| 세무·회계 담당 | 손으로 쓴 기록을 믿기 어렵다 | 차량별 해시 체인과 수정 이력으로 기록이 바뀐 흔적을 찾는다 |

## 심사 항목별 핵심 문장

| 심사 항목 | 핵심 문장 | 근거 (구현·확인) |
|---|---|---|
| 차량특화성 | 스마트폰 앱은 사람이 적어야 하지만, 이 앱은 **차가 직접 쓴다.** 주행 전·후 계기판 거리를 차량에서 읽고, 기어가 P를 벗어나면 운행을 시작해 P에서 끝내며, 주행 중에는 터치할 것이 없는 화면만 보여 준다 | Vehicle SDK Odometer, AAOS 기어·UX 제한 API. 에뮬레이터 + Virtual Console에서 확인 |
| 창의성 | 운전자(작성), 관리자(취합), 세무 담당(신뢰) 세 사람의 불편을 **"고칠 수 없는 기록"** 하나로 함께 푼다. 실시간 위치는 모으지 않고, 비업무 운행은 주소를 지우고 거리만 남겨 감시 논란을 피한다 | 차량별 해시 체인·수정 이력([integrity.md](integrity.md)), 비업무 주소 삭제. 단위 테스트와 서버 반영 확인 |
| 기술 활용성 | Pleos SDK 없이는 앱이 성립하지 않는다. 거리(Odometer), 차량 식별(VIN), 배터리 잔량(EvBattery), 출발·도착 주소(NaviHelper)를 핵심 기능에 쓰고, 화면을 보지 않아도 되게 운행 시작·정차·분류를 음성(TextToSpeech, 차량 안 엔진)으로 알린다 | 아래 SDK 활용표, [sdk-usage.md](sdk-usage.md) |
| 사업성 | 기업 하나를 들이면 **차량 수만큼 사용자**가 생기고, 월말 정산 때문에 매달 다시 쓴다. 법인 → 렌터카·리스 → 공유차·보험으로 넓힌다 | [business.md](business.md) (시장 규모·MAU·Retention 숫자는 팀이 공식 통계로 채움) |
| 가산점 | 시연 영상, GitHub(소스·APK) | GitHub 저장소, `apk/AltroStudio_DriveLog_release.apk`, 시연 시나리오 [demo-script.md](demo-script.md) (영상은 팀이 녹화) |

## Pleos SDK 활용표

| 앱 기능 | SDK · API | 권한 | 에뮬레이터 확인 |
|---|---|---|---|
| 주행 전·후 계기판 거리 자동 기입 | Vehicle · Odometer `getCurrentDistanceDriven`, `registerDistanceDriven` | `pleos.car.permission.CAR_DRIVE` | 확인 |
| 차량 식별 (조직 등록) | Vehicle · CarInfo `getVINInfo` | `pleos.car.permission.CAR_INFO` | 에뮬레이터 VIN이 "invalid"라 앱이 만든 기기 ID로 대신함 |
| 운행 전·후 배터리 잔량 | Vehicle · EvBattery `getEvBatteryStateOfCharge` | `pleos.car.permission.CAR_ENERGY` | 확인 |
| 연비·전비 (지원 차량만) | Vehicle · EvBattery / Fuel | `CAR_ENERGY` | 에뮬레이터 미지원 |
| 출발·도착 주소 | NaviHelper `getCurrentLocationInfo` | `pleos.car.permission.NAVI_ROUTE` | 확인 |
| 방문처 (목적지·경유지 도착) | NaviHelper `onDestinationArrived`, `onWayPointArrived` | `NAVI_ROUTE` | 코드 완료. 에뮬레이터가 경로 도착을 흉내 내지 못해 확인 못 함 |
| 음성 안내 (운행 시작, 정차 때 거리·시간·추천 용도, 분류 결과) | TextToSpeech `speak`, `stop` (`Mode.ON_DEVICE`) | `pleos.car.permission.TTS_SERVICE` | 확인 |
| 운행 시작·정차 판단 | AAOS `CarPropertyManager` `GEAR_SELECTION` (Vehicle `Gear`는 deprecated) | `android.car.permission.CAR_POWERTRAIN` | 확인 |
| 주행 중 조작 0 | AAOS `CarUxRestrictionsManager`, `distractionOptimized` | 없음 | 확인 |

## 개발 계획 (접수양식 5번 항목)

**Pleos Connect 환경에서 구현하는 방법**
- 차량 앱: Kotlin, Jetpack Compose, Room(차 안에 먼저 저장), WorkManager(연결되면 업로드). minSdk 28, targetSdk 35.
- 서버: Supabase(Postgres·인증·행 단위 권한). 차량은 비밀번호 없이 익명 로그인하고, 관리자가 발급한 6자리 연결 코드로 조직에 등록한다.
- 관리자 웹: HTML·CSS·JavaScript (외부 라이브러리 없음). 기업 계정과 개발자 계정.
- 서버가 없어도 차 안에서 기록·분류·월별 기록부·CSV가 모두 동작한다(오프라인 우선).

**주요 개발 범위와 일정**

| 시기 | 범위 | 상태 |
|---|---|---|
| ~11/13 (M1) | 자동 운행 감지, 정차 후 원탭 분류, 월별 운행기록부·CSV, 해시 체인, 차량 연결·동기화, 관리자 웹, 디자인 v0.1 | 구현·확인 완료 (일부 진행 중은 아래 표) |
| 11/23~12/2 (M2) | 열람자(회계·세무사) 역할, 월말 마감 승인, 운전자 배정·여러 명 선택, 웹 무결성 확인 배지, PT 시연 리허설 | 계획 |

## 구현 현황 요약

| 상태 | 기능 |
|---|---|
| 완료 | 기어로 운행 자동 감지, 계기판 거리 자동 기입, 주행 중 조작 0, 정차 후 원탭 분류와 지난번 분류 제안, 5분 뒤 미분류 종료, 강제 종료 후 복원, 출발·도착 주소, 비업무 주소 삭제, 배터리 잔량, 월별 운행기록부·CSV, 해시 체인·수정 이력, 동의·철회·개인정보 처리방침·오픈소스 라이선스, 차량 연결(6자리 코드), 오프라인 우선 동기화, 관리자 웹(기업 계정·개발자 계정), 세무서 제출용 서식(국세청고시 제2025-8호 별지와 같은 칸, 인쇄·PDF)과 명세서용 업무사용비율, 휴대폰으로 받기(QR 코드), 직원 회원가입(가입 코드·소속 정보·관리자 승인), 개발자의 기업 삭제, 운행기록부 기간 고르기(앱·웹), release APK 새 AVD 설치 확인 |
| 진행 중 | 방문처(내비 도착 이벤트, 에뮬레이터에서 확인 불가), 연비·전비(에뮬레이터 미지원), 실제 VIN(에뮬레이터 "invalid"), 부팅 후 자동 재시작(재부팅 시험 전), 디자인 v0.1(A04 이후 화면 명세 대기), 서버 일시 정지 방지(저장소 Secrets 등록 대기) |
| 계획 | 관리자의 운전자 추가 화면, 운전자의 웹 열람, M2 기능 전체 |

## 제출 정보

| 항목 | 값 |
|---|---|
| GitHub 저장소 | https://github.com/altrofast11x2/hmg_app_challenge_contest (비공개, 심사용 계정이 공지되면 Collaborator로 초대) |
| APK 경로 | `apk/AltroStudio_DriveLog_release.apk` |
| 관리자 웹 | https://altrostudio-drivelog.vercel.app |
| 시연용 차량 연결 코드 | `422511` (여러 번 사용 가능, 2027-02-28까지) |
| 관리자 웹 데모 계정 | 기업 계정 `demo@altro.com` · 비밀번호는 기획서에만 적는다 (저장소에 두지 않음). 개발자 계정은 팀 운영용이라 알려 주지 않는다 |

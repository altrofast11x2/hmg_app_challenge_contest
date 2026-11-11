# 기획서 요약 (접수양식에 옮길 내용)

> 2026-11-08 기준 (0.4.0: 앱 이름 차곡, 차 상태 보기, 사용법·음성 도움 비서 추가). 접수양식(기획서)에 그대로 옮기거나 줄여 쓸 수 있게 정리했다. 구현 여부는 [status.md](status.md)와 같고, 확인하지 않은 기능은 "계획"이나 "진행 중"으로 적었다. 사업성 숫자와 출처는 [business.md](business.md)에 있다 (가정은 가정이라고 밝힘).

## 앱 이름과 한 줄 소개

- **앱 이름**: 차곡 (Chagok): 차가 쓰는 운행기록부 ("차곡차곡 쌓이는 운행 기록")
- **한 줄 소개**: 법인·관공서의 공용차를 위한 차량용 앱. 운전자를 알아보고 그분의 온도·시트·무드등으로 차를 맞추고, 계기판 거리로 운행기록부를 차가 직접 쓰며, 정차 후 한 번 누르거나 말로 용도를 남기고, 내리기 전에는 창문과 실내등까지 정리한다.

## 해결하려는 불편

| 누구 | 지금의 불편 | 이 앱에서 |
|---|---|---|
| 운전자 | 운행마다 계기판 거리를 보고 적어야 하고, 몰아서 쓰게 된다 | 거리·시각·주소를 차가 직접 기록. 정차 후 용도만 한 번 누른다 |
| 차량 관리자 | 운전자별 기록부를 매달 걷고 확인해야 한다 | 관리자 웹에서 모든 차량의 월별 운행기록부와 CSV를 바로 본다 |
| 세무·회계 담당 | 손으로 쓴 기록을 믿기 어렵다 | 차량별 해시 체인과 수정 이력으로 기록이 바뀐 흔적을 찾는다 |
| 공용차 운전자 | 탈 때마다 좌석·온도를 다시 맞추고, 운행기록부 사용자 칸을 따로 적는다 | 운전자를 한 번 고르면(또는 말하면) 그분의 온도·시트 열선·무드등으로 차가 바뀌고 사용자 칸이 채워진다 |
| 다음 운전자·차량 관리자 | 창문이 열린 채, 배터리가 바닥난 채 차를 넘겨받는다. 반납 상태를 모른다 | 내리기 전에 차가 점검해 알리고 한 번에 정리, 관리자 웹 현황에 반납 상태 |

## 심사 항목별 핵심 문장

| 심사 항목 | 핵심 문장 | 근거 (구현·확인) |
|---|---|---|
| 차량특화성 | 스마트폰 앱은 사람이 적어야 하지만, 이 앱은 **차가 직접 쓰고, 직접 맞추고, 직접 정리한다.** 운전자를 고르면 공조·시트·무드등이 바뀌고(Vehicle SDK 제어), 정차하면 무드등이 신호를 보내고, 내리기 전에는 창문·실내등을 닫는다. 그리고 주행 전·후 계기판 거리를 차량에서 읽고, 기어가 P를 벗어나면 운행을 시작해 P에서 끝내며, 주행 중에는 터치할 것이 없는 화면만 보여 준다 | Vehicle SDK Odometer, AAOS 기어·UX 제한 API. 에뮬레이터 + Virtual Console에서 확인 |
| 창의성 | 운행기록부 작성을 **공용차 운영 전체(탑승 → 맞춤 → 기록 → 하차 정리 → 인계)** 로 넓혔다. 운전자 확인 한 번이 기록부 사용자 칸과 차량 맞춤을 동시에 해결하고, 무드등을 화면 대신 쓰는 신호로 쓴다. 운전자(작성), 관리자(취합·반납 상태), 세무 담당(신뢰) 세 사람의 불편을 **"고칠 수 없는 기록"** 하나로 함께 푼다. 실시간 위치는 모으지 않고, 비업무 운행은 주소를 지우고 거리만 남겨 감시 논란을 피한다 | 차량별 해시 체인·수정 이력([integrity.md](integrity.md)), 비업무 주소 삭제. 단위 테스트와 서버 반영 확인 |
| 기술 활용성 | Pleos SDK 없이는 앱이 성립하지 않는다. Vehicle SDK로 계기판 거리를 읽고 공조(HVAC)·시트 열선(Seat)·무드등·실내등(Light)·창문(Window)·문(Door)을 제어하며, 정차하면 차 안 음성 인식(SpeechToText, ON_DEVICE)으로 용도와 업무 내용을 직접 묻고 듣고, 거리(Odometer), 차량 식별(VIN), 배터리 잔량(EvBattery), 출발·도착 주소(NaviHelper)를 핵심 기능에 쓰고, 화면을 보지 않아도 되게 운행 시작·정차·분류를 음성(TextToSpeech, 차량 안 엔진)으로 알린다 | 아래 SDK 활용표, [sdk-usage.md](sdk-usage.md) |
| 사업성 | 기업 하나를 들이면 **차량 수만큼 사용자**가 생기고, 월말 정산 때문에 매달, 운전자 확인 때문에 **탈 때마다** 쓴다. Pleos Fleet API 문서에서 "지원 예정"인 운행 이력·운전자·정비 정보를 차 안에서 채워, Pleos Fleet 고객(법인 차량 관리)과 이어질 수 있다. 법인 → 렌터카·리스 → 공유차·보험으로 넓힌다 | [business.md](business.md): 국내 법인 명의 승용차 275만 대(2022), Pleos Connect 2030년 약 2,000만 대 탑재 목표 → 2030년 국내 차량 MAU 약 1.4만(기본, 보수 3,500~낙관 5.7만), 차량 M+3 Retention 목표 90% |
| 가산점 | 시연 영상, GitHub(소스·APK) | GitHub 저장소, `apk/AltroStudio_Chagok_release.apk`, 시연 시나리오 [demo-script.md](demo-script.md) (영상은 팀이 녹화) |

## Pleos SDK 활용표

| 앱 기능 | SDK · API | 권한 | 에뮬레이터 확인 |
|---|---|---|---|
| 주행 전·후 계기판 거리 자동 기입 | Vehicle · Odometer `getCurrentDistanceDriven`, `registerDistanceDriven` | `pleos.car.permission.CAR_DRIVE` | 확인 |
| 차량 식별 (조직 등록) | Vehicle · CarInfo `getVINInfo` | `pleos.car.permission.CAR_INFO` | 에뮬레이터 VIN이 "invalid"라 앱이 만든 기기 ID로 대신함 |
| 운행 전·후 배터리 잔량 | Vehicle · EvBattery `getEvBatteryStateOfCharge` | `pleos.car.permission.CAR_ENERGY` | 확인 |
| 연비·전비 (지원 차량만) | Vehicle · EvBattery / Fuel | `CAR_ENERGY` | 에뮬레이터 미지원 |
| 출발·도착 주소 | NaviHelper `getCurrentLocationInfo` | `pleos.car.permission.NAVI_ROUTE` | 확인 |
| 방문처 (목적지·경유지 도착) | NaviHelper `onDestinationArrived`, `onWayPointArrived` | `NAVI_ROUTE` | 코드 완료. 에뮬레이터가 경로 도착을 흉내 내지 못해 확인 못 함 |
| 음성 안내 (운행 시작, 정차 때 거리·시간·추천 용도, 분류 결과, 운전자 맞춤, 하차 점검) | TextToSpeech `speak`, `stop` (`Mode.ON_DEVICE`) | `pleos.car.permission.TTS_SERVICE` | 확인 |
| 운전자 맞춤: 운전석 공조 온도 | Vehicle · HVAC `setHvacTemperature` | `CAR_CLIMATE`, `CONTROL_CAR_CLIMATE` | 확인 (시스템 바 온도 변화) |
| 운전자 맞춤: 시트 열선·통풍 | Vehicle · Seat `setHvacSeatTemperature` | `CAR_SEATS`, `CONTROL_CAR_SEATS` | 확인 |
| 운전자 맞춤 무드등, 무드등 신호 | Vehicle · Light `setAmbientLightBrightnessColor`·`Brightness`·`Switch` | `CAR_LIGHTS` | 확인 |
| 하차 점검: 창문·실내등·문 | Vehicle · Window `setWindowPosition`, Light `setCabinLightSwitch`, Door `setDoorLock` | `CAR_WINDOWS`·`CAR_DOORS`·`CONTROL_CAR_WINDOWS`·`CONTROL_CAR_LIGHTS`·`CONTROL_CAR_DOORS` | 확인 |
| 말로 기록 (정차 후 용도·업무 내용 묻고 듣기, 업무 내용 되묻기·되읽기) | SpeechToText `request`, `stop`, `sendAudio` (`Mode.ON_DEVICE`) | `pleos.car.permission.STT_SERVICE` | 확인 (합성 음성으로 끝에서 끝까지, 실제 목소리는 실차에서) |
| (향후 계획) Gleo AI 음성 명령: 업무 일지·운전자 지정·요약 | Gleo Interface (`assets/schema.json` 함수 3개, 응답 방송) | 없음 | 공모전 참가팀은 App Schema 검토·승인을 신청할 수 없어(운영사무국 공지) 이번 출품 기능·시연에는 쓰지 않는다. 스키마와 처리 코드만 준비 |
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
| 11/3~11/4 | 공용차 운전자 프로필(맞춤 설정), 무드등 신호, 하차 점검·인계, 관리자 웹 현황·업무 일지·운전자, 제품 소개 페이지 | 구현·확인 (Gleo 음성 명령 처리 코드도 준비: 향후 계획) |
| 11/4 | 말로 기록(0.3.1): SpeechToText로 정차 후 용도·업무 내용 묻고 듣기 | 구현·확인 (실제 목소리는 실차에서) |
| 11/23~12/2 (M2) | 열람자(회계·세무사) 역할, 월말 마감 승인, 웹 무결성 확인 배지, PT 시연 리허설 | 계획 |

## 구현 현황 요약

| 상태 | 기능 |
|---|---|
| 완료 | 기어로 운행 자동 감지, 계기판 거리 자동 기입, 주행 중 조작 0, 정차 후 원탭 분류와 지난번 분류 제안, 5분 뒤 미분류 종료, 강제 종료 후 복원, 출발·도착 주소, 비업무 주소 삭제, 배터리 잔량, 월별 운행기록부·CSV, 해시 체인·수정 이력, 동의·철회·개인정보 처리방침·오픈소스 라이선스, 차량 연결(6자리 코드), 오프라인 우선 동기화, 관리자 웹(기업 계정·개발자 계정), 세무서 제출용 서식(국세청고시 제2025-8호 별지와 같은 칸, 인쇄·PDF)과 명세서용 업무사용비율, 휴대폰으로 받기(QR 코드), 직원 회원가입(가입 코드·소속 정보·관리자 승인), 개발자의 기업 삭제, 운행기록부 기간 고르기(앱·웹), release APK 새 AVD 설치 확인, 부팅 후 자동 재시작, 서버 일시 정지 방지(하루 두 번 깨우기), 공용차 운전자 맞춤(공조·시트 열선·무드등 제어), 무드등 신호, 하차 점검·정리, 관리자 웹 현황·업무 일지·운전자, 말로 기록(SpeechToText, 확인 단계·직접 입력), 차 상태 보기(정차 중 창문 닫기·문 잠그기·실내등 끄기), 첫 실행 사용법(음성 낭독)과 음성 도움 비서 |
| 진행 중 | 방문처(내비 도착 이벤트, 에뮬레이터에서 확인 불가), 연비·전비(에뮬레이터 미지원), 실제 VIN(에뮬레이터 "invalid"), 디자인 v0.1(A04 이후 화면 명세 대기), 실차 마이크·실차 시험. 향후 계획: Gleo AI 음성 명령(공모전 참가팀은 App Schema 검토·승인을 신청할 수 없어 처리 코드만 준비) |
| 계획 | 운전자의 웹 열람, M2 기능 전체 |

## 제출 정보

| 항목 | 값 |
|---|---|
| GitHub 저장소 | https://github.com/altrofast11x2/AltroStudio_DriveLog (심사 기준 커밋은 접수 메일에 적은 최종 커밋 번호) |
| APK 경로 | `apk/AltroStudio_Chagok_release.apk` |
| 관리자 웹 | https://altrostudio-drivelog.vercel.app/app.html (제품 소개 페이지: https://altrostudio-drivelog.vercel.app) |
| 시연용 차량 연결 코드 | `422511` (여러 번 사용 가능, 2027-02-28까지) |
| 관리자 웹 데모 계정 | 기업 계정 `demo@altro.com` · 비밀번호는 기획서에만 적는다 (저장소에 두지 않음). 개발자 계정은 팀 운영용이라 알려 주지 않는다 |

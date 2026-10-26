# Pleos SDK 활용 매핑

> **상태 (2026-10-26)**: 아래 API로 운행 자동 기록·분류·월별 기록부를 구현했고 Pleos Connect 에뮬레이터 + Virtual Console에서 검증했습니다. 기능별 상태는 `docs/status.md`에 있습니다.
> 시그니처는 공식 Maven 저장소의 소스 jar(Vehicle 2.3.2, NaviHelper 3.0.5)와 Pleos 공식 문서에서 확인했습니다.

## 기능별 사용 API

| 앱 기능 | SDK | API | 권한 | 에뮬레이터 검증 |
|---|---|---|---|---|
| 주행 전/후 계기판 거리 자동 기입 | Vehicle · Odometer | `getCurrentDistanceDriven(onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)`<br>`registerDistanceDriven(listener: DistanceDrivenListener)` → `onDistanceDrivenUpdated(value: Float?)`<br>`checkCurrentDistanceDrivenCapabilities(...)` | `pleos.car.permission.CAR_DRIVE` | ✅ Virtual Console Odometer 0→100 km가 약 1초 간격으로 실시간 반영 |
| 차량 식별 (연결 코드 입력 시 조직 등록) | Vehicle · CarInfo | `checkVINInfoCapabilities(...)`<br>`getVINInfo(onComplete: (String?) -> Unit, onFailed: (Exception) -> Unit)` | `pleos.car.permission.CAR_INFO` | ⚠️ 에뮬레이터는 `"invalid"`를 돌려준다. 17자리 VIN 형식이 아니면 값 없음으로 보고, 앱이 처음 연결할 때 만든 기기 ID로 차량을 구분한다 |
| 운행 전후 배터리 잔량 (전비 대신) | Vehicle · EvBattery | `checkEvBatteryStateOfChargeCapabilities(...)`<br>`getEvBatteryStateOfCharge(onComplete: (Int?) -> Unit, ...)` | `pleos.car.permission.CAR_ENERGY` | ✅ Virtual Console Battery SOC 100→80% 반영 |
| 연비·전비 (차량이 지원할 때만) | Vehicle · EvBattery / Fuel | `getCurrentEvEfficiency(...)`, `getCurrentFuelEconomy(...)` | `CAR_ENERGY` | ❌ 에뮬레이터 미지원 (순간·충전 후·누적 전비, 연료 값 모두 `not supported`). 실차에서 지원되면 함께 기록 |
| 운행 시작 주소, 종료 주소 | NaviHelper | `getCurrentLocationInfo()` → 리스너 `onCurrentLocationInfo(CurrentLocationInfo)`<br>`CurrentLocationInfo(latitude, longitude, heading, address, routeId, currentRouteIndex)` | `pleos.car.permission.NAVI_ROUTE` | ✅ 경로 안내가 없어도 주소가 온다 ("수정구 시흥동", 0.4~1.1초) |
| 방문처 (목적지·경유지 도착 기록) | NaviHelper | `onDestinationArrived(DestinationArrivedInfo(duration, distance, name, address))`<br>`onWayPointArrived(WaypointArrivedInfo(...))`, `getDestinationInfo()` → `onDestinationInfo(DestinationInfo)` | `NAVI_ROUTE` | 부분: 경로가 없으면 빈 값이 와서 값 없음으로 처리. 경로 안내 중 값과 도착 이벤트는 에뮬레이터에서 확인할 수 없다 (Virtual Console에 경로 시뮬레이션이 없고, 내비는 에뮬레이터 GPS `adb emu geo fix`를 따르지 않는다. 2026-10-26 확인). 도착 정보가 없으면 종료 주소를 비고로 쓴다 |

## Pleos SDK가 아닌 AAOS 표준 API

| 앱 기능 | API | 권한 | 에뮬레이터 검증 |
|---|---|---|---|
| 운행 시작·정차 판단 (기어) | `CarPropertyManager`의 `GEAR_SELECTION` (`useLibrary("android.car")`). Vehicle SDK의 `Gear` API는 deprecated이며 Google `GEAR_SELECTION`을 쓰라고 안내한다 | `android.car.permission.CAR_POWERTRAIN` | ✅ P/R/N/D 전환이 바로 반영 |
| 화면 조작 가능 여부 (주행 중 조작 0) | `CarUxRestrictionsManager` + 액티비티 `distractionOptimized` meta-data | 없음 | ✅ D로 바꾸면 0.15초 뒤 제한, P로 바꾸면 해제. `distractionOptimized`로 선언한 화면은 D에서도 시스템이 가리지 않는다 |
| 정차 판단 보조 (주차 브레이크) | `CarPropertyManager`의 `PARKING_BRAKE_ON` | `CAR_POWERTRAIN` (기어와 같은 권한) | 읽기는 된다 (`false`). Virtual Console의 경고등 카드로는 값이 바뀌지 않아 에뮬레이터 시연은 기어로만 한다 |

선언한 차량 권한 5개는 모두 설치 시 자동으로 허용된다 (2026-10-26 `dumpsys package`로 확인). 차 안에서 권한 허용 팝업이 뜨지 않는다. 이 밖에 인터넷(`INTERNET`, 조직에 연결한 차량의 서버 통신), 포그라운드 서비스, 부팅 완료 수신 권한을 쓰며 모두 설치 시 허용되는 일반 권한이다.

## M2 검토

| 항목 | API | 권한 | 비고 |
|---|---|---|---|
| 좌석 위치로 지난번 운전자 추천 | Vehicle · Seat `getCurrentSeatForeAftPosition`, `getCurrentSeatHeightPosition`, `getCurrentSeatBackrestAnglePosition`, `getCurrentSeatOccupancy` | `pleos.car.permission.CAR_SEATS` | Virtual Console에는 좌석 위치 조절이 없고 착석 여부만 있다 |

## 사용하지 않는 것과 이유

| 항목 | 이유 |
|---|---|
| Vehicle · Safety 경고등 (`getTelltalesInfo`, `registerTelltales`) | `CAR_SAFETY`만으로는 `SecurityException`이 나고, 리스너는 일반 앱이 쓸 수 없는 `PRIVATE_CAR_SAFETY` 권한이 필요하다. 주차 브레이크는 AAOS `PARKING_BRAKE_ON`으로 읽는다 |
| AAOS `EV_BATTERY_LEVEL` (배터리 에너지 kWh) | `android.car.permission.CAR_ENERGY`가 실행 중 허용이 필요한 권한이다. 배터리 잔량(%)으로 대신한다 |
| `android.car.permission.CAR_SPEED` | 실행 중 허용이 필요한 권한이다. 주행 판단은 기어와 UX 제한으로 대신한다 |
| FusedLocation, TextToSpeech, SpeechToText, Vehicle 제어(`CONTROL_*`) | 명세에 따라 사용하지 않음 |
| 실시간 위치 추적 | 주소는 운행 시작과 종료 두 시점에만 조회한다 |

## SDK 수명주기 (소스 확인)

- Vehicle: `Vehicle(context)` → `initialize()` (내부에서 서비스 연결 시작) → `getOdometer()`, `getCarInfo()`, `getEvBattery()`, `getFuel()` → `release()`. 모든 조회는 IPC 비동기 콜백(`onComplete`/`onFailed`)이고, 콜백이 끝나기 전에 `release()`하지 않는다. 첫 조회는 서비스 연결 때문에 1~2.4초가 걸렸고, 이후에는 0.4~0.8초였다.
- NaviHelper: `NaviHelper(context)` → `initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit)` → `addListener(NaviHelperEventListener)` → `get*Info()` (결과는 리스너로 도착) → `removeListener()` → `release()`. 앱을 막 켰을 때는 서비스 연결이 3초 넘게 걸린 적이 있어 연결은 최대 10초, 응답은 3초까지 기다린다.

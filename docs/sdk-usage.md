# Pleos SDK 활용 매핑

> **상태: 계획 (2026-10-26, 2단계 조사 결과)** — 아직 앱에 구현된 기능은 없습니다.
> 시그니처는 공식 Maven 저장소의 소스 jar(Vehicle 2.3.2, NaviHelper 3.0.5)와 Pleos 공식 문서에서 확인했습니다.
> 에뮬레이터에서 실제 호출을 확인하면 "검증" 열에 날짜와 결과를 적습니다.

## 기능별 사용 API

| 앱 기능 | SDK | API | 권한 (보호 수준) | 검증 |
|---|---|---|---|---|
| 주행 전/후 계기판 거리 자동 기입 | Vehicle · Odometer | `getCurrentDistanceDriven(onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)`<br>`registerDistanceDriven(listener: DistanceDrivenListener)` → `onDistanceDrivenUpdated(value: Float?)`<br>`checkCurrentDistanceDrivenCapabilities(...)` | `pleos.car.permission.CAR_DRIVE` (normal) | 3단계 |
| 이번 주행 거리·시간 (보조 표시) | Vehicle · Odometer | `getTripCurrentDrivingDistance(...)`, `getTripCurrentDrivingTime(onComplete: (Int?) -> Unit, ...)` | `CAR_DRIVE` | 3단계 |
| 차량 식별 (연결 코드 입력 시 조직 등록) | Vehicle · CarInfo | `checkVINInfoCapabilities(...)`<br>`getVINInfo(onComplete: (String?) -> Unit, onFailed: (Exception) -> Unit)` | `pleos.car.permission.CAR_INFO` (normal) | 3단계 |
| 운행 종료 시 연비 | Vehicle · Fuel | `checkCurrentFuelEconomyCapabilities(...)`, `getCurrentFuelEconomy(...)` | `pleos.car.permission.CAR_ENERGY` (normal) | 3단계 |
| 운행 종료 시 전비 | Vehicle · EvBattery | `checkCurrentEvEfficiencyCapabilities(...)`, `getCurrentEvEfficiency(...)` | `CAR_ENERGY` | 3단계 |
| 운행 시작 주소, 종료 주소 | NaviHelper | `getCurrentLocationInfo()` → 리스너 `onCurrentLocationInfo(CurrentLocationInfo)`<br>`CurrentLocationInfo(latitude, longitude, heading, address, routeId, currentRouteIndex)` | `pleos.car.permission.NAVI_ROUTE` (normal) | 3단계: 경로 안내가 없을 때도 주소가 오는지 |
| 방문처 (목적지·경유지 도착 기록) | NaviHelper | `onDestinationArrived(DestinationArrivedInfo(duration, distance, name, address))`<br>`onWayPointArrived(WaypointArrivedInfo(duration, distance, index, name, address))`<br>`onRouteStarted(RouteStartInfo)`, `getDestinationInfo()` → `onDestinationInfo(DestinationInfo)` | `NAVI_ROUTE` | 3단계 |

## Pleos SDK가 아닌 AAOS 표준 API

| 앱 기능 | API | 권한 | 이유 |
|---|---|---|---|
| 운행 시작·정차 판단 (기어) | `CarPropertyManager`의 `GEAR_SELECTION` (`useLibrary("android.car")`) | `android.car.permission.CAR_POWERTRAIN` (normal) | Vehicle SDK의 `Gear` API는 deprecated이며 "Google GEAR_SELECTION 속성을 쓰라"고 안내한다 |
| 화면 조작 가능 여부 (주행 중 조작 0) | `CarUxRestrictionsManager` + 액티비티 `distractionOptimized` meta-data | 없음 | 플랫폼의 주행 제한 판단(속도·기어·주차 브레이크·Auto Hold, 지역별 정책)을 그대로 따른다 |

## 검토 중

| 항목 | API | 권한 | 비고 |
|---|---|---|---|
| 주차 브레이크로 정차 판단 보조 | Vehicle · Safety `getTelltalesInfo(...)`, `registerTelltales(...)` (`TelltaleType.PARKING_BRAKE`, `ELECTRONIC_PARKING_BRAKE`, `AUTO_HOLD`) | `pleos.car.permission.CAR_SAFETY` (normal) | 사용 여부 결정 대기 |
| (M2) 좌석 위치로 지난번 운전자 추천 | Vehicle · Seat `getCurrentSeatForeAftPosition`, `getCurrentSeatHeightPosition`, `getCurrentSeatBackrestAnglePosition`, `getCurrentSeatOccupancy` | `pleos.car.permission.CAR_SEATS` (normal) | Virtual Console에는 좌석 위치 조절이 없고 착석 여부만 있다 |

## 사용하지 않는 것

- FusedLocation, TextToSpeech, SpeechToText, Vehicle 제어(`CONTROL_*`): 명세에 따라 사용하지 않음
- `android.car.permission.CAR_SPEED`: 실행 중 허용이 필요한 권한이다. 주행 판단은 기어와 UX 제한으로 대신한다
- 실시간 위치 추적: 주소는 운행 시작과 종료 두 시점에만 조회한다

## SDK 수명주기 (소스 확인)

- Vehicle: `Vehicle(context)` → `initialize()` (내부에서 서비스 연결 시작) → `getOdometer()`, `getCarInfo()`, `getFuel()`, `getEvBattery()`, `getSafety()`, `getSeat()` → `release()`. 모든 조회는 IPC 비동기 콜백(`onComplete`/`onFailed`)이고, 콜백이 끝나기 전에 `release()`하지 않는다.
- NaviHelper: `NaviHelper(context)` → `initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit)` → `addListener(NaviHelperEventListener)` → `get*Info()` (결과는 리스너로 도착) → `removeListener()` → `release()`

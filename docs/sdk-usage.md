# Pleos SDK 활용 매핑

> **상태 (2026-10-26)**: 아래 API로 운행 자동 기록·분류·월별 기록부를 구현했고 Pleos Connect 에뮬레이터 + Virtual Console에서 검증했습니다. 기능별 상태는 `docs/status.md`에 있습니다.
> 시그니처는 공식 Maven 저장소의 소스 jar(Vehicle 2.3.2, NaviHelper 3.0.5), TextToSpeech·SpeechToText 3.3.1 AAR의 클래스(`javap`), Pleos 공식 문서에서 확인했습니다.

## 기능별 사용 API

| 앱 기능 | SDK | API | 권한 | 에뮬레이터 검증 |
|---|---|---|---|---|
| 주행 전/후 계기판 거리 자동 기입 | Vehicle · Odometer | `getCurrentDistanceDriven(onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)`<br>`registerDistanceDriven(listener: DistanceDrivenListener)` → `onDistanceDrivenUpdated(value: Float?)`<br>`checkCurrentDistanceDrivenCapabilities(...)` | `pleos.car.permission.CAR_DRIVE` | ✅ Virtual Console Odometer 0→100 km가 약 1초 간격으로 실시간 반영 |
| 차량 식별 (연결 코드 입력 시 조직 등록) | Vehicle · CarInfo | `checkVINInfoCapabilities(...)`<br>`getVINInfo(onComplete: (String?) -> Unit, onFailed: (Exception) -> Unit)` | `pleos.car.permission.CAR_INFO` | ⚠️ 에뮬레이터는 `"invalid"`를 돌려준다. 17자리 VIN 형식이 아니면 값 없음으로 보고, 앱이 처음 연결할 때 만든 기기 ID로 차량을 구분한다 |
| 운행 전후 배터리 잔량 (전비 대신) | Vehicle · EvBattery | `checkEvBatteryStateOfChargeCapabilities(...)`<br>`getEvBatteryStateOfCharge(onComplete: (Int?) -> Unit, ...)` | `pleos.car.permission.CAR_ENERGY` | ✅ Virtual Console Battery SOC 100→80% 반영 |
| 연비·전비 (차량이 지원할 때만) | Vehicle · EvBattery / Fuel | `getCurrentEvEfficiency(...)`, `getCurrentFuelEconomy(...)` | `CAR_ENERGY` | ❌ 에뮬레이터 미지원 (순간·충전 후·누적 전비, 연료 값 모두 `not supported`). 실차에서 지원되면 함께 기록 |
| 운행 시작 주소, 종료 주소 | NaviHelper | `getCurrentLocationInfo()` → 리스너 `onCurrentLocationInfo(CurrentLocationInfo)`<br>`CurrentLocationInfo(latitude, longitude, heading, address, routeId, currentRouteIndex)` | `pleos.car.permission.NAVI_ROUTE` | ✅ 경로 안내가 없어도 주소가 온다 ("수정구 시흥동", 0.4~1.1초) |
| 방문처 (목적지·경유지 도착 기록) | NaviHelper | `onDestinationArrived(DestinationArrivedInfo(duration, distance, name, address))`<br>`onWayPointArrived(WaypointArrivedInfo(...))`, `getDestinationInfo()` → `onDestinationInfo(DestinationInfo)` | `NAVI_ROUTE` | 부분: 경로가 없으면 빈 값이 와서 값 없음으로 처리. 경로 안내 중 값과 도착 이벤트는 에뮬레이터에서 확인할 수 없다 (Virtual Console에 경로 시뮬레이션이 없고, 내비는 에뮬레이터 GPS `adb emu geo fix`를 따르지 않는다. 2026-10-26 확인). 도착 정보가 없으면 종료 주소를 비고로 쓴다 |
| 음성 안내: 운행 시작, 정차(거리·시간·지난번 이곳의 용도), 분류 결과 | TextToSpeech | `TextToSpeech(context, Mode.ON_DEVICE)` → `initialize(OnInitializeListener)` → `addEventListener(EventListener)` → `setLocale(Locale.KOREA)` → `speak(text, isFinal = true)`, `stop()` | `pleos.car.permission.TTS_SERVICE` | ✅ 에뮬레이터 음성 서비스(CAAS 4.2.10)로 세 안내 모두 재생(`onStart` → `onDone`, 목소리 seoyoon), 연결 4 ms, 정차 안내 중 분류하면 `stop()` 뒤 새 안내 재생, 설정에서 끄고 켜기 확인 (2026-10-28). `setAudioAttributes`로 알림 용도(`USAGE_NOTIFICATION`, `CONTENT_TYPE_SPEECH`)를 넘기고, 읽기 전에 앱이 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` 포커스를 받는다 (Pleos 오디오 포커스 정책). 기본값이면 음성 서비스가 미디어 용도·포커스 없이 재생했다 (`usage=1`) |

## 차량 제어 (0.3.0, 공용차 운전자 맞춤·무드등 신호·하차 점검)

구현: `data/vehicle/PleosCabinControl.kt`. 모든 제어는 `check*Capabilities`가 준 영역만 쓰고, 바꾼 뒤 다시 읽어 같은 값일 때만 성공으로 본다 (Vehicle SDK 샘플 앱 가이드). 창문·실내등·문 잠금은 기어가 P이고 주행 제한이 없을 때 운전자가 직접 누를 때만 바꾼다 (`domain/cabin/CabinAssistant.kt`).

| 앱 기능 | SDK · API | 권한 | 에뮬레이터 검증 (2026-11-03~04) |
|---|---|---|---|
| 운전자 맞춤: 운전석 공조 온도 | Vehicle · HVAC `checkHvacTemperatureCapabilities`, `getHvacTemperature`, `setHvacTemperature(SeatArea, Float, CELSIUS)` | `CAR_CLIMATE`, `CONTROL_CAR_CLIMATE` | ✅ 22.0 → 24.0 → 23.0, Pleos 시스템 바 운전석 온도가 바로 바뀜 |
| 운전자 맞춤: 운전석 시트 열선·통풍 | Vehicle · Seat `checkSeatTemperatureCapabilities`, `getHvacSeatTemperature`, `setHvacSeatTemperature` (ROW_1_LEFT -3~3, 음수는 통풍) | `CAR_SEATS`, `CONTROL_CAR_SEATS` | ✅ 0 → 2, 시스템 바 열선 아이콘 |
| 운전자 맞춤 무드등 색, 무드등 신호 | Vehicle · Light `checkAmbientLightBrightnessColorCapabilities`, `get/setAmbientLightBrightnessColor`(ARGB, 알파 0xFF), `get/setAmbientLightBrightness`, `get/setAmbientLightSwitch` | `CAR_LIGHTS` (normal) | ✅ 영역은 좌석 다섯 개를 묶은 SeatArea 하나 (운전석 단독 영역은 "area not supported"). 다시 읽으면 알파가 0 |
| 하차 점검: 열린 창문, 창문 닫기 | Vehicle · Window `checkWindowPositionCapabilities`, `getCurrentWindowPosition`, `setWindowPosition` (0~10, 최솟값 = 닫힘) | `CAR_WINDOWS`, `CONTROL_CAR_WINDOWS` | ✅ Virtual Console로 연 창문을 읽고, [한 번에 정리]로 닫힘 |
| 하차 점검: 실내등 | Vehicle · Light `checkCabinLightSwitchCapabilities`, `get/setCabinLightSwitch` | `CAR_LIGHTS`, `CONTROL_CAR_LIGHTS` | ✅ 켜짐 → 꺼짐 |
| 하차 점검: 문 잠김, [문 잠그기] | Vehicle · Door `checkDoorLockCapabilities`(4도어 묶음 영역, 보닛 영역은 따로), `getDoorLockState`, `setDoorLock` | `CAR_DOORS`, `CONTROL_CAR_DOORS` | ✅ 잠금 해제 → 잠김 |
| 하차 점검: 배터리 부족 | Vehicle · EvBattery `getEvBatteryStateOfCharge` (20% 미만) | `CAR_ENERGY` | ✅ 18% 안내 |

- 에뮬레이터 Virtual Console 화면(`/vehicle/state`)은 앱이 바꾼 공조·창문·잠금·무드등을 다시 그리지 않는다. 앱의 다시 읽기와 Pleos 시스템 바로 확인한다. 반대로 Virtual Console에서 바꾼 창문·실내등·잠금·공조는 앱이 읽는다.
- 좌석 위치(`getCurrentSeatForeAftPosition` 등)는 에뮬레이터에서 늘 0이라(Virtual Console에서 바꿔도, 앱이 `moveSeatForeAft`로 움직여도) 좌석 위치로 운전자를 알아보거나 좌석을 맞추는 기능은 넣지 않았다.
- Pleos Playground 문서(create-my-project, 2026-11-03)는 Vehicle Control SDK를 "준비 중"으로 적는다. 개발 단계에서는 manifest 선언만으로 쓸 수 있고(온라인 설명회 10/28), App Market에 올릴 때 승인이 필요하다. 팀은 법인이 없어 App Market 등록은 법인 설립 뒤로 미뤘고(2026-11-04), 공모전 제출·시연(에뮬레이터)에는 승인이 필요 없다.
- 타이어 공기압은 Pleos `getTirePressure`가 `PRIVATE_CAR_SAFETY`, AAOS `TIRE_PRESSURE`가 signature 권한이라 쓰지 않는다.

## 향후 계획: 음성 명령 (Gleo Interface)

> 운영사무국 공지(개발 범위 안내)에 따라 공모전 참가팀은 Gleo Interface(App Schema) 검토·승인을 신청할 수 없다. 그래서 아래는 준비만 해 둔 것이고 이번 출품 기능과 시연에는 쓰지 않는다. 말로 기록은 다음 절의 SpeechToText만으로 동작한다.

`app/src/main/assets/schema.json`에 함수 3개를 등록하고(component `service` = 방송), `service/GleoActionReceiver`가 받아 `domain/voice/GleoActions`가 처리한 뒤 `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE` 방송으로 답한다 (status success/asking/fail, 영어 message, data). 7초 제한이라 6초 안에 답한다.

| 함수 | 매개변수 | 동작 |
|---|---|---|
| `RECORD_TRIP_PURPOSE` | `purpose`(COMMUTE·BUSINESS·PERSONAL, 필수), `workNote` | 주행 중이면 이번 운행에 적어 두고 정차하자마자 그 용도로 마친다. 정차 중이면 바로 마친다. 운행이 없으면 30분 안에 마친 운행을 고친다. 업무 내용은 기록부 비고에, 수정 이력 `voice:` |
| `SET_DRIVER` | `driverName`(필수) | 이름(공백·"님"·성 생략 허용)으로 운전자를 찾아 맞춤 설정 적용. 못 찾으면 asking + 명단 |
| `GET_DRIVING_SUMMARY` | `period`(TODAY·THIS_MONTH·LAST_MONTH) | 운행 수, 총거리, 업무용 거리, 업무사용비율 |

에뮬레이터 Gleo AI(`ai.umos.ambientai`)가 설치 때 스키마를 읽는 것을 로그(`[GAA] [Schema] Loaded schema.json from com.altrostudio.drivelog`)로 확인했고, 세 함수는 Gleo와 같은 방송을 adb로 보내 확인했다. Pleos 문서상 스키마는 Pleos 검토·승인 뒤 활성화된다. 공모전 기간에는 신청하지 않는다 (공모전 뒤에 쓸 초안: [gleo-schema-request.md](gleo-schema-request.md)).

## 말로 기록 (SpeechToText, 0.3.1)

정차하면 앱이 직접 묻고 듣는다. 음성 인식은 Pleos SpeechToText의 차 안 엔진만 쓰고, 목소리를 외부 음성 인식 서비스로 보내지 않는다. 구현: `data/voice/PleosListener.kt`(SDK), `domain/voice/VoiceJournal.kt`(대화), `domain/voice/SpokenTrip.kt`(받아 적은 글자 해석).

| 순서 | 동작 |
|---|---|
| 1. 묻기 | 정차 안내 끝에 "용도와 업무 내용을 말씀해 주세요"(지난번 이곳의 용도가 있으면 "맞으면 네, 아니면 용도를 말씀해 주세요") — 음성 안내와 [말로 용도 기록] 설정이 켜져 있을 때 |
| 2. 듣기 | 안내가 끝나고(`PleosSpeaker.busy`) 0.3초 뒤 최대 8초. 분류 화면의 [말로 기록]으로 언제든 다시 듣는다 |
| 3. 해석 | "업무, 거래처 납품 미팅" → 일반업무 + 업무 내용. "출근"·"퇴근길에 …" → 출퇴근. "비업무"·"개인 …" → 비업무(업무 내용 버림). "출근 아니고 업무"처럼 고쳐 말하면 뒤의 말. 용도 낱말 없이 거래처·미팅·납품 등이면 일반업무. "취소"·"나중에"면 그만 |
| 4. 되묻기 | 일반업무인데 업무 내용이 없으면 "어떤 업무였는지 말씀해 주세요" 한 번. 용도를 못 알아들으면 "출퇴근, 업무, 비업무 중에 말씀해 주세요"(두 번까지), 그래도 모르면 화면에서 고르게 한다 |
| 5. 확인 | 기록 전에 반드시 되읽고 묻는다: "일반업무, 업무 내용 거래처 미팅. 맞으면 맞아, 다르면 다시 말씀해 주세요." "맞아"·"그렇죠"·"다음"·"네" 또는 화면 [맞아요] → 기록. "아니"·"다시" → 다시 듣기. 다른 내용을 말하면 그 내용으로 다시 확인. 용도 버튼을 누르면 그 용도 + 들은 업무 내용으로 기록. 대답이 없으면 확인 화면을 남긴다 (2026-11-05 사용자 요청) |
| 6. 기록 | Gleo와 같은 `TripRecorder.recordByVoice` (수정 이력 `voice:`, 직접 입력은 `driver:`). 확인 때 되읽었으므로 분류 안내는 용도만 읽는다 |
| 직접 입력 | 분류 화면 [직접 입력] → 용도 고르기 + 업무 내용 글자 칸(Pleos CarIME 한국어 키보드). 들은 내용이 있으면 미리 채운다. 음성 인식이 늦거나 짧은 말을 놓칠 때 (2026-11-05 사용자 요청) |

- SDK: `SpeechToText(context, Mode.ON_DEVICE)` → `initialize(OnInitializeListener)` → `addListener(ResultListener)` → `setLocale(Locale.KOREA)` → `request()` / `stop()`. 결과는 `onUpdated(stt, completed)`, 끝은 `onEndedRecognition()`, 오류는 `onError(ErrorType)`. 권한 `pleos.car.permission.STT_SERVICE`(normal, 설치 시 허용 확인). 서버 모드(`HYBRID`·`SERVER`)는 `registerApp(Client ID, Client Secret)`이 필요해 쓰지 않는다 (비밀값은 APK에 넣지 않는 규칙). 차 안 엔진은 목소리가 차 밖으로 나가지 않는다.
- 듣는 동안 `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`(`USAGE_ASSISTANT`) 포커스를 받는다. 못 받으면(통화 중) 듣지 않는다. 앱은 녹음을 받지 않고 받아 적은 글자만 받으며, 그 글자를 로그에 남기지 않는다.
- 듣는 중 운전자가 화면에서 고르거나 다시 출발하면 바로 멈춘다(`VoiceJournal`이 운행 상태를 지켜본다). 자동으로 물었는데 말이 없으면 조용히 끝낸다.
- `PERMISSION_DENIED`·`NOT_SUPPORTED_API`·`UNAVAILABLE_LANGUAGE`면 앱을 다시 켤 때까지 자동으로 묻지 않는다([말로 기록] 버튼은 다시 시도). 엔진 오류면 다음에 처음부터 다시 연결한다.

에뮬레이터 실측 (2026-11-04, CAAS·GAAS 4.2.10):
- ✅ `ON_DEVICE` 엔진이 동작한다 (`inferenceSource: on_device`). 마이크 경로: `request()` → 차량 마이크(Built-In Mic) 열림, 시스템 바에 마이크 표시 → 말이 없으면 엔진은 스스로 끝내지 않아 앱의 8초 제한으로 끝남.
- ✅ 사람이 말할 수 없어서 Windows 한국어 음성(Heami)으로 만든 16 kHz·16비트·모노 PCM을 `sendAudio()` + `completeAudioSend()`로 넣어 시험했다 (debug 빌드의 `debug/VoiceFeedReceiver`, release에는 없음). "거래처 납품 미팅"은 그대로 받아 적었다.
- ⚠️ 음성 구간 검출(EPD)이 말 사이 약 1초 쉼에서 끝난 것으로 본다: "업무, 거래처 납품 미팅"이 "업무"만 왔다 → 그래서 업무 내용을 한 번 더 묻는 4단계를 넣었다.
- ⚠️ 합성 음성의 짧은 "네"를 "근데"로 받아 적었다 → 다시 묻기로 이어져 두 번째 대답으로 기록됐다. 한 음절 대답은 잘못 들을 수 있어 되읽기와 다시 묻기를 둔다.
- ✅ 끝에서 끝: 운행 → 정차 → 안내(질문 포함) → 0.3초 뒤 듣기 → "업무" → "어떤 업무였는지…" → "거래처 납품 미팅" → 일반업무 + 비고 "거래처 납품 미팅", 해시 체인 검증 통과. release 0.3.1에서 듣는 중 [일반업무]를 누르면 마이크가 바로 닫힌다.

사람 목소리 실측 (2026-11-04~05, 에뮬레이터 PC 마이크 `adb emu avd hostmicon` + 헤드셋):
- 엔진의 끝 판정이 짧다: "업무 거래처 | 납품 미팅"이 "업무 거래처"에서 끝났다. 공개 SDK에는 끝 판정 시간을 바꾸는 방법이 없다(그런 기능이 있는 `AmbientAiApi`는 `@PrivilegedApi`, 시스템 앱 전용). → `PleosListener`가 한 구간이 끝나면 바로 다시 `request()`하고 1초 안에 말이 이어지면 붙인다 (다시 듣기까지 약 0.1초).
- 중간 결과와 최종 결과가 다를 수 있다: 중간 "업무" → 최종 "어 모어". 짧은 말("다시", "맞아")은 말소리 시작조차 못 잡을 때가 있다. → 기록 전 확인 단계와 [맞아요]·[직접 입력] 버튼을 둔다.
- 확인 단계를 넣은 뒤 "업무 거래처 미팅" → "맞아"로 일반업무 + 비고 "거래처 미팅"이 기록됐다.

## Pleos SDK가 아닌 AAOS 표준 API

| 앱 기능 | API | 권한 | 에뮬레이터 검증 |
|---|---|---|---|
| 운행 시작·정차 판단 (기어) | `CarPropertyManager`의 `GEAR_SELECTION` (`useLibrary("android.car")`). Vehicle SDK의 `Gear` API는 deprecated이며 Google `GEAR_SELECTION`을 쓰라고 안내한다 | `android.car.permission.CAR_POWERTRAIN` | ✅ P/R/N/D 전환이 바로 반영 |
| 화면 조작 가능 여부 (주행 중 조작 0) | `CarUxRestrictionsManager` + 액티비티 `distractionOptimized` meta-data | 없음 | ✅ D로 바꾸면 0.15초 뒤 제한, P로 바꾸면 해제. `distractionOptimized`로 선언한 화면은 D에서도 시스템이 가리지 않는다 |
| 정차 판단 보조 (주차 브레이크) | `CarPropertyManager`의 `PARKING_BRAKE_ON` | `CAR_POWERTRAIN` (기어와 같은 권한) | 읽기는 된다 (`false`). Virtual Console의 경고등 카드로는 값이 바뀌지 않아 에뮬레이터 시연은 기어로만 한다 |

선언한 차량 권한 6개(음성 안내 `TTS_SERVICE` 포함, 보호 수준 normal)는 모두 설치 시 자동으로 허용된다 (2026-10-26 `dumpsys package`로 확인). 차 안에서 권한 허용 팝업이 뜨지 않는다. 이 밖에 인터넷(`INTERNET`, 조직에 연결한 차량의 서버 통신과 하루 한 번 새 버전 확인), 포그라운드 서비스, 부팅 완료 수신 권한을 쓰며 모두 설치 시 허용되는 일반 권한이다. Vehicle SDK 퍼미션은 App Market에 올릴 때 사전 협의·승인이 필요하다 (Pleos 심사 가이드, [pleos-guide-check.md](pleos-guide-check.md)).

## M2 검토

| 항목 | API | 권한 | 비고 |
|---|---|---|---|
| 좌석 위치로 지난번 운전자 추천 | Vehicle · Seat `getCurrentSeatForeAftPosition`, `getCurrentSeatHeightPosition`, `getCurrentSeatBackrestAnglePosition`, `getCurrentSeatOccupancy` | `pleos.car.permission.CAR_SEATS` | 보류: 에뮬레이터는 좌석 위치를 앱에 늘 0으로 준다 (2026-11-03). 실차에서 읽히면 다시 검토 |

## 사용하지 않는 것과 이유

| 항목 | 이유 |
|---|---|
| Vehicle · Safety 경고등 (`getTelltalesInfo`, `registerTelltales`) | `CAR_SAFETY`만으로는 `SecurityException`이 나고, 리스너는 일반 앱이 쓸 수 없는 `PRIVATE_CAR_SAFETY` 권한이 필요하다. 주차 브레이크는 AAOS `PARKING_BRAKE_ON`으로 읽는다 |
| AAOS `EV_BATTERY_LEVEL` (배터리 에너지 kWh) | `android.car.permission.CAR_ENERGY`가 실행 중 허용이 필요한 권한이다. 배터리 잔량(%)으로 대신한다 |
| `android.car.permission.CAR_SPEED` | 실행 중 허용이 필요한 권한이다. 주행 판단은 기어와 UX 제한으로 대신한다 |
| FusedLocation | 위치는 두 시점 주소만(NaviHelper) |
| SpeechToText 서버 모드(`HYBRID`, `SERVER`) | `registerApp`에 Client Secret이 필요해 쓰지 않는다. 차 안 엔진(`ON_DEVICE`)만 쓴다 |
| Vehicle 제어 중 좌석 이동(`moveSeat*`)·창문 열기·문 열기·트렁크 | 사람이 타 있는 좌석·창문을 앱이 먼저 움직이지 않는다. 창문은 닫기만, 문은 잠그기만 운전자가 누를 때 한다 |
| TextToSpeech 서버 모드(`HYBRID`, `SERVER`) | Pleos Playground My Project의 Client Secret을 APK에 넣어야 해서 쓰지 않는다(비밀값은 APK에 넣지 않는 규칙). 차량 안 엔진(`ON_DEVICE`)은 네트워크와 앱 등록이 필요 없고, 읽는 문장이 차 밖으로 나가지 않는다 |
| 실시간 위치 추적 | 주소는 운행 시작과 종료 두 시점에만 조회한다 |

## SDK 수명주기 (소스 확인)

- Vehicle: `Vehicle(context)` → `initialize()` (내부에서 서비스 연결 시작) → `getOdometer()`, `getCarInfo()`, `getEvBattery()`, `getFuel()` → `release()`. 모든 조회는 IPC 비동기 콜백(`onComplete`/`onFailed`)이고, 콜백이 끝나기 전에 `release()`하지 않는다. 첫 조회는 서비스 연결 때문에 1~2.4초가 걸렸고, 이후에는 0.4~0.8초였다.
- NaviHelper: `NaviHelper(context)` → `initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit)` → `addListener(NaviHelperEventListener)` → `get*Info()` (결과는 리스너로 도착) → `removeListener()` → `release()`. 앱을 막 켰을 때는 서비스 연결이 3초 넘게 걸린 적이 있어 연결은 최대 10초, 응답은 3초까지 기다린다.
- TextToSpeech: `TextToSpeech(context, Mode.ON_DEVICE)` → `initialize(OnInitializeListener)` (`onSuccess`/`onFailure(ErrorType)`) → `addEventListener(EventListener)` (`onReady`, `onStart`, `onUpdatedRms`, `onDone`, `onStop`, `onError`) → `setLocale(Locale.KOREA)` → `speak(text, isFinal)` / `stop()` → `removeEventListener()` → `release()`. 권한이 없으면 SDK가 `SecurityException`을 던진다. 앱은 처음 안내할 때 연결하고(연결되는 동안 들어온 안내는 가장 최근 것만), 10초 안에 연결되지 않으면 다음 안내 때 다시 연결한다(`data/voice/PleosSpeaker.kt`). 읽는 중에 새 안내가 오면 `stop()` 뒤 읽는다. 읽기 전에 오디오 포커스(`MAY_DUCK`)를 받고(못 받으면 읽지 않음) 150 ms 뒤 `speak()` (Pleos TTS 선행 묵음 가이드의 재생 지연 방식), `onDone`·`onStop`·`onError` 뒤 반납, 30초 넘게 쥐지 않는다. SDK가 공식 의존성으로 `universal-access`(Pleos 보안 모듈, 네이티브 라이브러리 포함)를 함께 가져와 release APK가 27 MB → 45 MB가 됐다. 서버 모드에서만 쓰는 모듈이지만 `TtsServiceManager`가 생성할 때 만들므로 빼지 않는다.

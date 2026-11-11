# Gleo AI 앱 스키마 검토 신청 초안 (공모전 뒤에 쓸 것)

> **2026-11-11**: 운영사무국 공지(공모전 개발 범위 및 기술 문의 안내)에 따라 공모전 참가팀은 Gleo Interface(App Schema) 검토·승인을 신청할 수 없다. 공모전 기간에는 이 메일을 보내지 않고, Gleo 음성 명령은 향후 계획으로 둔다. 아래는 공모전 뒤 App Market 준비 때 쓸 초안이다.

> 2026-11-04 작성. Pleos 앱 연동 가이드(Gleo Interface)는 "App Schema를 전달받으면 가이드라인 준수를 검토하고, 승인 후 통합이 자동으로 활성화된다"고만 적고, 보내는 곳은 정하지 않았다("추가 안내는 문서 작성자에게 문의").
> 그래서 Pleos 문의처 **partnership@pleos.ai**로 보내고, 필요하면 공모전 문의처 **appdevchallenge@hyundai-partners.com**을 참조(CC)에 넣는다. 보내는 사람은 팀 대표 메일.

> 승인되지 않아도 앱의 말로 기록은 동작한다 (0.3.1, Pleos SpeechToText 차 안 엔진으로 정차 후 직접 묻고 듣기, [sdk-usage.md](sdk-usage.md)). 승인되면 Gleo로 아무 때나 말하는 길이 하나 더 생긴다.
> 스키마 파일은 0.3.0 때와 같다 (`version` "0.3.0" 그대로, 앱만 0.3.1).

## 보낼 것

| 항목 | 값 |
|---|---|
| 첨부 | `app/src/main/assets/schema.json` (APK에도 같은 파일이 들어 있음: `apk/AltroStudio_Chagok_release.apk`) |
| 앱 이름 / 패키지 | 자동 운행기록부 (DriveLog) / `com.altrostudio.drivelog` |
| 개발사 | AltroStudio (현대자동차그룹 차량용 앱 개발 공모전 참가팀) |
| Pleos Playground 프로젝트 ID | `d2e82bc9-88ab-43b4-90a4-7fc6fa4975ef` |
| 앱 버전 | 0.3.0 (versionCode 3). App Market에는 아직 등록 전 (App Market Console 가입이 지금은 법인만 가능) |

## 메일 초안

> 제목: [Gleo Interface] 앱 스키마 검토 요청: 자동 운행기록부(com.altrostudio.drivelog)
>
> 안녕하세요, 현대자동차그룹 차량용 앱 개발 공모전에 참가한 AltroStudio입니다.
> Pleos Connect 앱 「자동 운행기록부(DriveLog)」의 Gleo Interface App Schema 검토를 요청드립니다. Pleos 앱 연동 가이드에 따라 `assets/schema.json`을 앱에 넣었고, 파일을 첨부합니다.
>
> - 패키지: com.altrostudio.drivelog / Pleos Playground 프로젝트 ID: d2e82bc9-88ab-43b4-90a4-7fc6fa4975ef
> - 앱 소개: 법인 공용차를 위한 자동 운행기록부입니다. 계기판 거리로 업무용승용차 운행기록부를 차가 직접 쓰고, 운전자별 맞춤 설정과 하차 점검을 합니다.
> - 함수 (component: service, 모두 방송으로 받고 `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE`로 답합니다)
>   1. RECORD_TRIP_PURPOSE(purpose: COMMUTE·BUSINESS·PERSONAL, workNote): 지금 또는 방금 운행의 용도와 업무 내용을 운행기록부에 남깁니다. 주행 중에 말하면 정차할 때 기록합니다.
>   2. SET_DRIVER(driverName): 공용차 운전자를 지정하고, 그 운전자의 저장된 공조 온도·시트 열선·무드등 설정을 적용합니다.
>   3. GET_DRIVING_SUMMARY(period: TODAY·THIS_MONTH·LAST_MONTH): 운행기록부의 총거리·업무용 거리·업무사용비율을 답합니다.
> - 응답: status(success/asking/fail), 영어 message, data(JSON 배열 문자열). 앱이 직접 소리로 알리는 경우에는 mode "beep". 7초 제한 안에(6초 이내) 답합니다.
> - 개인정보: 앱은 Gleo AI가 넘겨준 글자(용도, 업무 내용, 이름)만 받고 음성은 받지 않습니다. 업무 내용은 비업무 운행이면 저장하지 않습니다.
> - 확인한 것: Pleos Connect 에뮬레이터에서 Gleo AI가 설치 때 스키마를 읽는 로그(`[Schema] Loaded schema.json`)를 확인했고, Gleo Action과 같은 방송(ACTION_PARAM)을 보내 세 함수의 동작과 응답을 확인했습니다.
>
> 검토해 주시고, 승인 뒤 에뮬레이터와 실차에서 활성화되는 방법(재설치·재부팅 필요 여부)이나 고칠 점이 있으면 알려 주세요.
> 감사합니다. AltroStudio 드림

## 가이드 대조 (2026-11-04)

| 가이드 (App Schema 작성 가이드라인) | schema.json |
|---|---|
| 파일 이름 `schema.json`, 위치 `assets/` | 확인: `app/src/main/assets/schema.json` |
| actionCalls 최대 3개 | 확인: 3개 |
| function 이름: 대문자 + 밑줄, 고유, 동사만이 아니라 대상 포함 | 확인: RECORD_TRIP_PURPOSE, SET_DRIVER, GET_DRIVING_SUMMARY |
| description은 영어, 한두 문장, 구체적인 예시 금지, Gleo 동작 조작 문구 금지 | 확인: (workNote 설명의 예시 "such as …"는 2026-11-04에 뺌) |
| 매개변수 type은 string·number·boolean·array 중 하나, enum은 문자열만 | 확인: 모두 string, enum 문자열 |
| required에는 필수 매개변수만 | 확인: purpose / driverName / period |
| 각 function의 intent action 필터 (component service면 방송 수신자) | 확인: `service/GleoActionReceiver` (AndroidManifest의 RECORD_TRIP_PURPOSE·SET_DRIVER·GET_DRIVING_SUMMARY) |
| version = App Market에 등록된 버전 | 주의: App Market 등록 전이라 앱 버전 0.3.0을 적음 (메일에 설명) |
| Callback: functionName·status·message(영어)·data·mode | 확인: `domain/voice/GleoActions.kt` |

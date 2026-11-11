# Pleos Playground 가이드 대조표

> 2026-10-28, Pleos Playground 문서(https://document.pleos.ai/docs/pleos-only/getting-started 이하 시작하기·기획·디자인·개발·테스트·등록·심사 가이드 약 140쪽)를 처음부터 읽고 앱을 맞췄습니다.
> **맞춤**: 가이드대로 동작하고 확인함 · **고침**: 이번에 어긋난 것을 찾아 고침 · **해당 없음**: 앱 성격상 대상이 아님 · **남음**: 앱 밖의 일(서류·심사 신청)

## 1. 기획·설계 (vehicle-app-planning-guide)

| 가이드 | 요구 | DriveLog | 상태 |
|---|---|---|---|
| 앱 카테고리 | 설계 때부터 카테고리를 정한다 | **Business**(업무·생산성). 제출 정보는 [app-market.md](app-market.md) | 맞춤 |
| 주행 안전을 고려한 설계 | CarUxRestrictions로 주행 중 기능 축소, 시선 분산 최소화, 터치 최소화, 음성 연계 | `CarUxRestrictionsManager` + `distractionOptimized`. 주행 중에는 터치 요소가 없는 화면만, 운행 시작·정차·분류 결과는 음성(TextToSpeech)으로 알림 | 맞춤 |
| 지역별 주행 규제 | 주행 중 키보드·설정·검색 제한 | 주행 중 화면에 입력·설정 없음. 연결 코드 입력(키패드)·설정은 정차 중에만 | 맞춤 |
| 앱 구조 및 동작 모델 | portrait·landscape 모두 설계, 창 크기가 바뀌어도 핵심 기능 유지, 백그라운드에서 돌아오면 상태 복원 | 1200dp 기준 2분할(portrait) 배치, 화면 상태는 `rememberSaveable`, 운행은 DB에 즉시 저장 후 복원 | 맞춤 |
| 성능 및 리소스 | 주행 중 CPU 5% 미만(내비 아닌 앱), 정차 중 25% 이하, RAM 400 MB 이하, 빠른 콜드 스타트, 백그라운드 타이머·폴링 최소화 | 에뮬레이터 측정(2026-10-28, `top`·`dumpsys meminfo`·`am start -W`): 콜드 스타트 0.71초, 메모리 PSS 74~81 MB, 정차 중 CPU 0~0.4%, 주행 중 계기판이 1초마다 바뀔 때 1.7% (D로 바꾸는 순간 10초 평균 5.2% — 주행 화면 전환·음성 안내·DB 저장이 한꺼번에). 기록 엔진은 기어·계기판 변화 구독(폴링 없음), 동기화는 WorkManager. "이번 달" 확인이 화면이 꺼져 있어도 1분마다 돌던 것을 다음 달 시작 시각까지 기다리도록(최대 1시간) **고침**. 실차 측정은 남음 | 고침 |
| 보안 및 개인정보 | 동의 흐름을 쉽게, 불필요한 PII 미저장, **로그의 개인정보 마스킹** | 동의 화면(A01), 비업무는 주소 즉시 삭제. 기록 엔진이 실패 로그에 방문처 이름·주소를 남기던 것을 입력 종류만 남기도록 **고침** | 고침 |
| API 호환성 | Vehicle SDK는 `check*Capabilities`로 확인, TextToSpeech는 전체 호환 | 모든 Vehicle 조회 전에 capability 확인, 지원 안 하면 값 없음으로 기록 | 맞춤 |

## 2. 디자인 (Pleos Connect UI Design Guide)

| 가이드 | 요구 | DriveLog | 상태 |
|---|---|---|---|
| Easy & Safe | 주행 중 동영상·애니메이션·자동 스크롤 금지, 한 손 조작 | 반복 애니메이션 없음(누름 축소 효과만, 인터랙션 가이드의 95% shrink와 같음) | 맞춤 |
| Glanceability | 입력 후 0.25초 안에 반응, 늦으면 진행 중 표시 | 누름 효과 100 ms (`drivelog_press_duration`), 서버를 기다리는 버튼(연결 코드 확인, 휴대폰으로 받기)은 누르는 즉시 로딩 표시 | 맞춤 |
| Display Layout / Window | Landscape·Portrait 두 배치 | 위 "앱 구조" 참고. 폼 팩터 콘솔(가상 디스플레이) 시험은 남음 | 맞춤 |
| Typography | 최소 12sp | 가장 작은 글자 24px (`drivelog_label_small`) | 맞춤 |
| App Icon | Adaptive Icon(전경·배경 분리) | adaptive icon(배경·전경) + 단색(themed) 레이어 **추가**. 마켓용 512 PNG는 [app-market-assets/icon-512.png](app-market-assets/icon-512.png) | 고침 |

## 3. 개발 (vehicle-app-developer-guide)

| 가이드 | 요구 | DriveLog | 상태 |
|---|---|---|---|
| 주행 규제 개발 | DO 앱도 `onUxRestrictionsChanged`로 기능별 제한 적용 | 위와 같음 | 맞춤 |
| **CCP 개발 (필수)** | 최상위 화면에서 Back 하드키를 누르면 앱을 끝내지 말고 `moveTaskToBack(true)`로 숨김. `finish()` 금지(반려 대상) | 홈·동의·첫 연결 화면의 뒤로 가기와 동의 화면 [앱 닫기]가 `finish()`/시스템 기본이었다 → `MainActivity`가 `moveTaskToBack(true)`. 에뮬레이터에서 설정→뒤로→홈, 홈→뒤로 = `moveTaskToBack`(액티비티·프로세스 유지) 확인 | 고침 |
| CCP 지원 선언 | 사용자 설치 앱은 선택. 미지원이면 `com.hmg.rotary.ROTARY_SUPPORTED=false`, 모든 조작을 터치로 완결 | 다이얼 포커스 UX를 설계·시험하지 않았으므로 미지원 선언 추가 | 고침 |
| **오디오 포커스** | 재생 전 `AudioAttributes` + `AudioFocusRequest`, 음악과 함께라면 `GAIN_TRANSIENT_MAY_DUCK`, 포커스 변화 처리 | 음성 서비스가 안내를 미디어 용도·포커스 없이 재생하던 것을 확인 → 알림 용도(`USAGE_NOTIFICATION`, `CONTENT_TYPE_SPEECH`)로 `MAY_DUCK` 포커스를 받은 뒤 읽고, 못 받으면(통화·긴급 안내) 읽지 않으며, 끝나면 반납, 잃으면 멈춤. 에뮬레이터 `CarAudioService` 포커스 보유자·`usage=5` 확인 | 고침 |
| TTS 선행 묵음 | 음성 안내 전 150~200 ms 묵음 | 음성 데이터는 음성 서비스가 만들어 앞에 묵음을 넣을 수 없어, 포커스를 받은 뒤 150 ms 늦게 읽기 시작 (가이드의 "재생 지연" 방식) | 고침 |
| Suspend 모드 오디오 | 일반 오디오 포커스는 반납 | 안내마다 받고 끝나면 반납, 30초 넘게 쥐지 않음 | 맞춤 |
| 실행 환경 FAQ | 지역 코드·플랫폼 확인 | 한국 세무 서식 앱이라 지역 분기 없음 | 해당 없음 |
| 음악·내비 앱 가이드 | 미디어 세션·내비 연동 | 미디어·내비 앱 아님 | 해당 없음 |

## 4. 테스트 (app-test-guide)

| 가이드 | DriveLog | 상태 |
|---|---|---|
| Virtual Console | 기어·계기판·배터리로 운행 전체 시험 (README 시험 순서) | 맞춤 |
| 폼 팩터 테스트 (FormFactorConsole + host-display-bridge, Go 1.22) | 12.9·14.6·17인치와 Wide 가상 디스플레이 시험은 아직. 지금은 `wm density`로 2분할 배치를 확인 | 남음 |

## 5. 등록·배포 (app-registration-guide, distribute)

| 가이드 | 요구 | DriveLog | 상태 |
|---|---|---|---|
| **앱 보안 — 민감 데이터 암호화** | 기기에 저장하는 민감정보(SQLite, SharedPreferences 포함)는 앱 수준에서 암호화, 키는 Android Keystore | 운행 기록 DB(주소·방문처·VIN)가 평문 SQLite, 기기 계정 토큰·VIN이 평문 SharedPreferences였다 → DB는 SQLCipher(AES-256), 설정의 VIN·차량 ID·토큰·조직 연결·DB 키는 Keystore AES-256-GCM. 이전 버전의 평문 DB·설정은 처음 열 때 암호화본으로 옮김(에뮬레이터의 기존 운행 17건 그대로, 체인 이어짐 확인) | 고침 |
| 앱 보안 — 로그 | 출시 앱 로그에 민감정보 금지 | 위 1절. DB 이전 SQL에 키를 바인딩으로 넘겨 SQLite 오류 로그에 키가 찍히지 않게 함 | 고침 |
| 앱 보안 — URL | 토큰·민감정보를 URL 경로·쿼리에 넣지 않음 | 기기 확인 요청이 `?id=eq.<VIN>`으로 VIN을 주소에 담았다 → 목록을 받아 앱에서 확인(RLS로 자기 차량만 보임). 토큰은 `Authorization` 헤더. [휴대폰으로 받기]의 1회용 링크는 `#` 뒤(서버로 가지 않음), 10분 만료 | 고침 |
| 앱 보안 — 컴포넌트 노출 | 외부에서 부를 필요 없는 컴포넌트는 노출하지 않음 | `BootReceiver`가 `exported=true`였다 → `false` (부팅 완료 방송은 시스템이 보내므로 그대로 받음, 재부팅 후 서비스 자동 시작 확인) | 고침 |
| 앱 보안 — 통신 | HTTPS, TLS 1.2 이상 | OkHttp 기본(TLS 1.2·1.3, MODERN_TLS), Android 9+ 평문 통신 차단 | 맞춤 |
| 앱 보안 — 배포 | 배포용 인증서, 디버깅 비활성화, 비밀 하드코딩 금지 | release는 `CN=AltroStudio DriveLog` 인증서(키는 저장소 밖), debuggable 아님. Supabase 공개 키(publishable)만 들어가고 service_role·TTS Client Secret은 넣지 않음 | 맞춤 |
| 앱 보안 — 백업 | 쓰지 않는 자동 백업 비활성화, 민감 데이터는 백업에서 제외 | `allowBackup=false`, Android 12+ `dataExtractionRules`, Android 11 이하 `fullBackupContent`(모든 영역 제외, 이번에 **추가**) | 고침 |
| **앱 버전 관리** | 배포·심사 요청마다 버전을 올림 | versionCode 1 → 2, versionName 0.1.0 → 0.2.0. 규칙은 [app-market.md](app-market.md) | 고침 |
| 앱 업데이트 뒤 기록 재시작 | 사용자가 직접 업데이트하면 실행 중인 앱이 꺼진다 | 업데이트 뒤 앱을 열 때까지 기록 서비스가 꺼져 있던 것을 `MY_PACKAGE_REPLACED`로 바로 다시 켜도록 **고침**. 에뮬레이터에서 새 APK 설치 → 화면을 열지 않아도 포그라운드 서비스 시작(`PACKAGE_REPLACED`) 확인 | 고침 |
| **앱 업데이트** | App Market은 자동 업데이트를 하지 않음 → 앱이 버전 확인, "최신 버전이 존재합니다…" 안내, `market://detail?package=` 이동 | 하루 한 번 관리자 웹의 `app-version.json`을 받아 새 버전이면 정차 중 홈에서 안내 → [이동] 시 앱 마켓 상세. 열 수 없으면 안내 문구. 에뮬레이터에서 안내창 확인(에뮬레이터 앱 마켓은 이 주소를 처리하지 않음) | 고침 |
| 앱 태그 표기 | 공식 영문명 태그 필수 | 태그 `DriveLog`(영문 필수) + `자동 운행기록부` 등 ([app-market.md](app-market.md)) | 맞춤 |
| 심사 — 개인정보 | 항목·목적·보유기간·거부할 권리 고지, 동의 버튼 외의 행동을 동의로 보지 않음, 철회 시 삭제 | A01 동의 화면(거부할 권리 카드, "동의하고 시작"만 동의), 철회 시 차 안 기록 삭제·서버 연결 해제 | 맞춤 |
| 심사 — 등록정보 | 개인정보처리방침 URL, 위치정보 이용 시 위치정보 약관 URL, 심사용 계정, 아이콘 512, 스크린샷 | 처리방침 공개 페이지 https://altrostudio-drivelog.vercel.app/privacy.html (앱 문구에서 자동 생성), 아이콘·스크린샷 5장 [app-market-assets/](app-market-assets/). 위치정보 약관은 초안 [location-terms-draft.md](location-terms-draft.md) — 법률 검토 남음 | 남음 |
| 심사 — 기능·성능 | 더미 데이터·임시 파일 없음, 10초 안에 실행 | 데모 데이터는 `backend/seed`에만, Fake는 debug 빌드에만 | 맞춤 |
| SDK 퍼미션 | Vehicle SDK 퍼미션은 App Market과 사전 협의·승인 | Playground My Project "자동 운행기록부"에서 Vehicle 조회·NaviHelper 권한이 **사용 가능**임을 확인(2026-10-28). TextToSpeech는 Playground에 "준비 중"으로 표시(신청 불가) — 차량 안 엔진은 등록 없이 동작 | 맞춤 |

## 6. 이번에 하지 않은 것 (제안)

| 항목 | 이유 |
|---|---|
| Gleo Interface (음성 명령, `assets/schema.json` + intent action) | 예: "방금 운행 업무로 기록해". 차량특화·기술 활용성에 도움이 되지만 새 기능이고, Pleos의 스키마 검토·승인이 있어야 켜지며 에뮬레이터에서 시험할 수 없다. 2026-11-11 운영사무국 공지: 공모전 참가팀은 검토·승인을 신청할 수 없다 → 향후 계획으로 두고 출품 기능에는 쓰지 않는다 |
| SpeechToText로 음성 분류 | 새 기능. 마이크 권한·개인정보 고지가 늘어난다 |
| CCP(다이얼) 지원 | 포커스 UX 설계와 실차 시험이 필요하다. 지금은 미지원 선언 |

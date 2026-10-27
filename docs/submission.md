# 공모전 제출 점검표

> 2026-10-27 기준. 공모전 사이트의 안내 문구를 항목별로 옮기고 이 저장소의 상태를 적었다. ✅ 맞춤 · ⏳ 팀이 할 일 · ➖ 해당 없음.
> 출처: [지원·제출 방법](https://appdevchallenge.auto-hmg.io/guide/submission) · [심사·시상](https://appdevchallenge.auto-hmg.io/guide/judging-awards) · [개발 환경](https://appdevchallenge.auto-hmg.io/guide/development) · [일정](https://appdevchallenge.auto-hmg.io/guide/schedule) · [참가 규정](https://appdevchallenge.auto-hmg.io/guide/rules) · [출품 유의사항](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000006) · [FAQ](https://appdevchallenge.auto-hmg.io/faq)

## 1. GitHub 저장소 (가산점)

| 공모전 안내 | 상태 | 이 저장소 |
|---|---|---|
| 새 저장소를 만들거나 기존 개발 저장소를 쓴다 | ✅ | `altrofast11x2/hmg_app_challenge_contest` (main) |
| 프로젝트 소스 코드를 업로드한다 | ✅ | `app/`(차량 앱), `web/`(관리자 웹), `backend/`(서버) |
| APK는 저장소 안 `apk/` 폴더에, 파일명 `팀명_앱이름_release.apk` | ✅ | `apk/AltroStudio_DriveLog_release.apk` |
| 실행 가능한 release 버전 | ✅ | v2 서명, debuggable 아님. 새로 만든 AVD에 설치해 동의 → 연결 → 주행 → 분류 → 서버 반영까지 확인 |
| GitHub 업로드 용량을 넘으면 Releases에 올리고 그 주소를 제출 | ➖ | 약 27 MB라 저장소에 그대로 둔다 |
| README.md: 앱 이름 및 한 줄 소개 | ✅ | [README](../README.md#앱-이름-및-한-줄-소개) |
| README.md: 실행 방법 | ✅ | [README](../README.md#실행-방법) (APK로 바로 해 보기 / 소스에서 빌드 / 관리자 웹) |
| README.md: 테스트 환경 및 필요한 설정 | ✅ | [README](../README.md#테스트-환경-및-필요한-설정) (버전, 에뮬레이터, Virtual Console, 데모 계정) |
| README.md: APK 파일 위치 또는 Release 링크 | ✅ | [README](../README.md#apk-파일-위치) |
| 저장소 주소와 APK 파일 경로를 제출 페이지(접수양식)에 적는다 | ⏳ | 저장소 `https://github.com/altrofast11x2/hmg_app_challenge_contest`, APK 경로 `apk/AltroStudio_DriveLog_release.apk` ([기획 요약](plan-summary.md#제출-정보)) |
| 비공개 저장소는 공지된 심사용 계정을 Collaborator로 초대 | ⏳ | 비공개로 두고 초대하기로 결정(2026-10-27). 심사용 계정은 아직 공지되지 않음 → 공지가 나오면 초대 |
| 마감 전에 심사용 계정이 접근되는지 반드시 확인 | ⏳ | 초대 후 확인 |
| 개인 계정의 비공개 저장소는 쓰기 권한도 주어지므로 미리 확인 | ⏳ | 초대 전 확인 |

## 2. 접수 (필수: 기획서)

| 공모전 안내 | 상태 | 비고 |
|---|---|---|
| 접수양식(기획서) 작성·서명 후 이메일 제출: appdevchallenge@hyundai-partners.com | ⏳ | 심사 항목별 문장·SDK 활용표·개발 계획은 [plan-summary.md](plan-summary.md) |
| 메일 제목 `[차량용 앱 개발 공모전] 팀명_대표자명`, 팀원 전원 참조(CC) | ⏳ | 수정 제출은 `[수정제출]`을 앞에 붙이고 전체 자료와 서명한 동의서를 다시 첨부 |
| 개인정보 수집·이용 동의서: 팀원 각자 서명 | ⏳ | |
| 접수 마감 2026-11-15(일) | ⏳ | 팀 목표 11/13 |
| 접수양식 5번(개발 계획): 구현 방법, 주요 개발 범위, SDK/API 사용 계획 | ✅ | [plan-summary.md](plan-summary.md#개발-계획-접수양식-5번-항목) |

## 3. 심사 기준 (1차, 필수 4항목)

| 심사 항목 | 공모전 설명 | 이 앱의 근거 |
|---|---|---|
| 차량특화성 | 모바일 대비 차량 환경만의 가치, 주행·정차 맥락과 차량 기능의 결합 | 계기판 거리를 차가 직접 기록, 기어로 운행 시작·끝, 주행 중 조작 0, 정차 후 원탭 분류 |
| 창의성 | 독창성, 운전자·동승자의 불편과 해결 방식 | 운전자(작성)·관리자(취합)·세무 담당(신뢰)을 "고칠 수 없는 기록"으로, 차에는 공유 앱이 없어 QR로 휴대폰에 넘기기 |
| 기술 활용성 | Pleos Playground SDK·API를 핵심 기능과 사용 경험에 활용 | Vehicle(Odometer·CarInfo·EvBattery), NaviHelper(출발·도착 주소, 방문처) — [sdk-usage.md](sdk-usage.md) |
| 사업성 | 타깃 고객, 예상 MAU, Retention Rate, 서비스 확대 계획 | [business.md](business.md) 계산 틀과 확대 계획. **⏳ 시장 규모·MAU·Retention 숫자는 팀이 공식 통계로 채워야 한다** |

## 4. 가산점 · 그 밖

| 공모전 안내 | 상태 | 비고 |
|---|---|---|
| 프로토타입 시연 영상(화면 녹화 포함) | ⏳ | 팀이 OBS로 녹화. 장면표는 [demo-script.md](demo-script.md) |
| 숏폼 챌린지(선택, 가산점 없음): 30초~1분 세로 영상, 인스타그램 릴스·유튜브 쇼츠, 필수 해시태그 `#HMG차량용앱개발공모전`, 링크는 접수양식에 | ⏳ | 숏폼용 장면 3개는 demo-script.md 끝 |
| 개발 환경: Android API 28 이상, Gradle 8.0 이상, Java 또는 Kotlin | ✅ | minSdk 28, Gradle 8.10.2, Kotlin 2.0.0 |
| 순수 창작물, 제3자 권리 침해 금지 | ✅ | 오픈소스는 앱 안과 [licenses.md](licenses.md)에 고지, 현대자동차그룹·Pleos 로고·상표 미사용 |
| 제출 내용이 사실과 다르면 선정 취소 | ✅ | [status.md](status.md)에 완료·진행 중·계획을 확인 방법과 함께 적는다 |
| 2차 PT 대상은 PT 전까지 실제 동작하는 앱 완성 (1차 합격팀은 개발 산출물 필수) | ✅ | 에뮬레이터에서 실제 동작. 2차 PT 2026-12-03~04 |
| 최종 10팀은 전시용 구동 APK 제출, 2027-02 행사 시연 | ➖ | 시연용 연결 코드는 2027-02-28까지 유효 |

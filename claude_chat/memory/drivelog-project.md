---
name: drivelog-project
description: "공모전 출품 아이디어는 '자동 운행기록부(DriveLog)'로 확정(2026-10-25). 명세는 claude_chat/SPEC.md, 11단계로 진행"
metadata:
  node_type: memory
  type: project
  originSessionId: a0acef44-095f-4903-afed-69862aa322b3
  modified: 2026-10-26T05:03:30.059Z
---

2026-10-25에 사용자가 아이디어를 '자동 운행기록부'(DriveLog, 패키지 com.altrostudio.drivelog)로 정하고 상세 명세를 줬다. 명세 원문은 저장소의 `claude_chat/SPEC.md`에 있다. 그 전 세션(10/22~24)에서 검토한 아이디어(멀미 제로, 로드 도슨트 등)는 폐기했다.

- 마일스톤: M1 11/13까지(1차 서류 마감 11/15), M2 11/23~12/2(12/3~4 PT에서 실제 동작 시연 필수). M2 기능은 사용자가 시작하라고 할 때만 만든다.
- 진행: 명세 11번의 단계별 진행. 단계마다 멈추고 보고하고, 커밋한다. Pleos SDK 클래스·메서드는 추측하지 말고 공식 문서(WebFetch)로 확인한다.
- 2026-10-26: 1단계(프로젝트 생성, assembleDebug, AVD 설치·실행 확인)를 마치고 push했다. 2단계(GMS 확인, 문서 조사)를 마치고 구현 계획·백엔드 제안을 보고했다. 사용자 결정을 받은 뒤 3단계로 간다.
- 사용자 정보: 심사위원은 APK를 내려받아 설치해서 동작을 확인한다 → release APK는 키(공개 anon 키)가 들어간 상태로 새 AVD에서 바로 동작해야 하고, 심사위원이 따라 할 데모 연결 코드·계정이 필요하다. Pleos 심사 가이드도 "인증 키 누락"을 설치 오류 사례로 든다.

- 2026-10-26 사용자 결정 (2단계 추천안 전부 수락):
  1. 백엔드 Supabase (차량은 익명 로그인 + 6자리 연결 코드 RPC로 조직 등록)
  2. 앱: Room + WorkManager + OkHttp·kotlinx.serialization으로 Supabase REST 직접 호출 (supabase-kt 미사용), DI는 수동
  3. 운행 종료: P 상태에서 분류 탭 즉시, 또는 P 5분 지속 시 "미분류"로 종료. 신호 대기·Auto Hold는 종료 아님. 데모용으로 대기 시간 조절 가능
  4. 주차 브레이크를 정차 판단 보조로 사용. 단 Pleos `registerTelltales`는 PRIVATE 권한이라, 3단계에서 `getTelltalesInfo`(CAR_SAFETY)와 AAOS `PARKING_BRAKE_ON`(CAR_POWERTRAIN)을 비교해 정하고 보고한다
  5. 경로 없는 주행의 주소는 NaviHelper만 사용. 안 오면 방문처 빈칸 → 웹에서 수정(이력 남김). 위치 권한 미사용

- 2026-10-26 3단계 결정: 전비 API가 에뮬레이터 미지원이라 운행 전후 배터리 잔량(%)을 기록하고 실차에서 전비가 지원되면 함께 기록한다. VIN이 없으면(에뮬레이터 "invalid") 앱이 처음 연결할 때 만든 기기 ID로 차량을 구분하고 웹에 "VIN 미확인" 표시. 주차 브레이크는 AAOS PARKING_BRAKE_ON(추가 권한 없음)으로 바꾸고 CAR_SAFETY는 쓰지 않는다. 3단계 완료, 다음은 4단계(Room, 상태 머신, 해시 체인, 테스트).

- 2026-10-26 4단계 완료 (목표 11/1보다 일찍): Room(trips, trip_stops, trip_changes, chain_heads), 순수 상태 머신, TripRecorder, 해시 체인(docs/integrity.md), 포그라운드 서비스(specialUse)+부팅 수신기, 단위 테스트 28개. 에뮬레이터에서 분류 완료·30초 미분류 종료·앱 강제 종료 후 복원을 검증했다. 동의 화면이 없어서 지금은 debug 확인 화면의 "기록 시작" 버튼으로 동의 처리한다. 다음은 5단계(동의, 주행 중 화면, 정차 후 분류, 월별 기록부, CSV, 라이선스 화면).

**Why:** 사용자가 공모전 규정(정직한 구현 현황, 순수 창작물, 비밀값 미포함)을 기술 취향보다 우선한다고 명시했다.

**How to apply:** 새 세션은 SPEC.md와 docs/status.md(생기면)를 먼저 읽는다. 저장소·브랜치 규칙은 [[git-branch-workflow]], 환경 함정은 [[pleos-dev-environment]], 비밀값 규칙은 [[secrets-policy]].

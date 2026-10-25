---
name: drivelog-project
description: "공모전 출품 아이디어는 '자동 운행기록부(DriveLog)'로 확정(2026-10-25). 명세는 claude_chat/SPEC.md, 11단계로 진행"
metadata:
  node_type: memory
  type: project
  originSessionId: a0acef44-095f-4903-afed-69862aa322b3
  modified: 2026-10-25T15:15:59.966Z
---

2026-10-25에 사용자가 아이디어를 '자동 운행기록부'(DriveLog, 패키지 com.altrostudio.drivelog)로 정하고 상세 명세를 줬다. 명세 원문은 저장소의 `claude_chat/SPEC.md`에 있다. 그 전 세션(10/22~24)에서 검토한 아이디어(멀미 제로, 로드 도슨트 등)는 폐기했다.

- 마일스톤: M1 11/13까지(1차 서류 마감 11/15), M2 11/23~12/2(12/3~4 PT에서 실제 동작 시연 필수). M2 기능은 사용자가 시작하라고 할 때만 만든다.
- 진행: 명세 11번의 단계별 진행. 단계마다 멈추고 보고하고, 커밋한다. Pleos SDK 클래스·메서드는 추측하지 말고 공식 문서(WebFetch)로 확인한다.
- 2026-10-26: 1단계(프로젝트 생성, assembleDebug, AVD 설치·실행 확인)를 마쳤다. 다음은 2단계(GMS 확인, 문서 읽기, 구현 계획과 백엔드 제안)다.

**Why:** 사용자가 공모전 규정(정직한 구현 현황, 순수 창작물, 비밀값 미포함)을 기술 취향보다 우선한다고 명시했다.

**How to apply:** 새 세션은 SPEC.md와 docs/status.md(생기면)를 먼저 읽는다. 저장소·브랜치 규칙은 [[git-branch-workflow]], 환경 함정은 [[pleos-dev-environment]], 비밀값 규칙은 [[secrets-policy]].

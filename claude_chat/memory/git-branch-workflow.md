---
name: git-branch-workflow
description: "작업 저장소는 비공개 altrofast11x2/hmg_app_challenge_contest의 altrofast11x2 브랜치, 백업은 altrofast11x2_backup, push 전 확인"
metadata:
  node_type: memory
  type: project
  originSessionId: a0acef44-095f-4903-afed-69862aa322b3
  modified: 2026-10-25T15:16:00.242Z
---

- 작업 폴더 `C:\Users\kazin\AndroidStudioProjects\DriveLog` = 비공개 저장소 `altrofast11x2/hmg_app_challenge_contest`의 `altrofast11x2` 브랜치. 명세의 "새 비공개 저장소 DriveLog 생성"은 사용자 지시로 이것으로 바뀌었다.
- 2026-10-26: 사용자가 "altrofast11x2 branch 안에 있는거 다 지워도됨"이라고 해서 기존 내용(이전 아이디어 탐색 프로젝트 com.hmgchallenge.app, 이전 claude_chat)을 지우고 새로 시작했다. 지우기 전 상태 `5f8b4cb`는 `altrofast11x2_backup` 브랜치로 push해 두었다.
- `main`(팀원이 README 수정, cf64586)과 `nrioig`는 팀원 브랜치다. 직접 push하지 않는다. LICENSE(MIT, Cozy)는 main과 같은 파일이라 유지한다.

**Why:** 명세 0번이 "외부로 나가는 작업(저장소 생성, push, 배포)은 실행 전에 확인"을 요구한다. 팀원과 같은 저장소를 쓴다.

**How to apply:** 커밋은 자유롭게 하되 `altrofast11x2` push는 매번 사용자에게 확인받는다. 관련: [[drivelog-project]]

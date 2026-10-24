---
name: project-status
description: "2026-10-24 기준 공모전 진행 상태 — 개발 준비 완료, 아이디어 미정(사용자가 정한 뒤 개발 시작), 미커밋 변경 있음"
metadata:
  node_type: memory
  type: project
  originSessionId: 63f35f8b-b151-42be-a526-b897e5c6ee6a
  modified: 2026-10-24T14:30:37.880Z
---

2026-10-24에 개발 준비를 끝냈다. 사용자가 "아이디어 생각하고 올게, 그때 개발하자고 하면 개발 시작하자"고 했다. **아이디어가 정해지기 전에는 기능 개발을 시작하지 않는다.**

- 일정: 접수 마감 2026-11-15, 1차 발표 11-23, PT 12-3~4 (동작하는 앱 필수)
- 사용자 요청으로 작업 트리를 GitHub 상태(origin/altrofast11x2 = b68e08a)로 리셋했다. 테스트 권한 매니페스트(`app/src/debug/AndroidManifest.xml`)와 `useLibrary("android.car")`는 `git stash`에 "테스트 권한(debug 매니페스트) + Car API useLibrary (2026-10-24)" 이름으로 보관했다. 개발을 시작할 때 `git stash pop`으로 되살릴지 사용자에게 묻는다. 사용자는 이 권한을 "지우지 말라"고 했다. 브랜치는 [[git-branch-workflow]]
- 지금까지 제안한 아이디어: 멀미 제로(추천), 로드 도슨트, 졸음 방어선, 펫 모드, 와이퍼 기상청. 그 전에 제안한 것: 쉼표(휴식 모드), 여행 퀘스트, 차량 주치의, 드라이브 무드. 사용자는 "아이디어가 제일 문제"라고 했다.
- 아이디어를 정할 때 지킬 제약은 [[pleos-dev-environment]]에 있다: 시트 눕힘 제어 불가, ADAS·주변 차량 데이터 사용 불가, 속도는 AAOS Car API로 읽음.
- 외부 LLM API 키는 앱에 넣어도 된다 ([[api-key-in-app-ok]]). 키는 아직 받지 않았다.

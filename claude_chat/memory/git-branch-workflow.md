---
name: git-branch-workflow
description: "공모전 저장소는 팀원별 브랜치로 작업 — 사용자 브랜치는 altrofast11x2, main은 팀원도 수정함"
metadata:
  node_type: memory
  type: project
  originSessionId: 63f35f8b-b151-42be-a526-b897e5c6ee6a
  modified: 2026-10-24T12:58:46.099Z
---

2026-10-24부터 사용자는 `altrofast11x2` 브랜치에 작업을 push한다. 첫 커밋 b68e08a(기본 프로젝트 세팅)를 이 브랜치에 올렸다. 원격에는 다른 팀원의 브랜치(예: `nrioig`)도 있고, `main`에는 팀원이 README를 직접 수정한 커밋(cf64586)이 있다.

**Why:** 사용자가 "branch 는 altrofast11x2 로 잡아서 push"하라고 지시했다. 팀원이 각자 브랜치를 쓰는 협업 구조다.

**How to apply:** 기본적으로 `altrofast11x2`에 커밋하고 push한다. `main`에는 직접 push하지 않는다. `main`에 합칠 때는 README 충돌 가능성을 먼저 확인한다. 저장소 위치는 [[contest-dev-repo-location]]에 있다.

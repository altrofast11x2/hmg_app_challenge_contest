---
name: api-key-in-app-ok
description: "공모전 앱에 외부 LLM API 키를 직접 넣어도 됨 — 사용자가 결정, 보안 경고 반복하지 말 것"
metadata:
  node_type: memory
  type: feedback
  originSessionId: 63f35f8b-b151-42be-a526-b897e5c6ee6a
  modified: 2026-10-24T13:28:41.584Z
---

외부 LLM API 키를 앱에 직접 넣어도 된다. 중계 서버를 따로 만들 필요는 없다. (2026-10-24 사용자 결정)

**Why:** 저장소(altrofast11x2/hmg_app_challenge_contest)는 GitHub private이고, 사용자가 공모전이 끝난 뒤 키를 교체하거나 삭제할 계획이다. 사용자 말: "api 걱정하지마 github private 로 할거야. 앱에 넣어도됨."

**How to apply:** LLM 연동을 구현할 때 키 노출 경고나 프록시 서버를 다시 권하지 않는다. 키는 `local.properties` → `BuildConfig`처럼 간단한 방식으로 넣는다. 관련 메모리: [[pleos-dev-environment]], [[git-branch-workflow]]

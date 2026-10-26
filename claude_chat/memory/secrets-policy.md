---
name: secrets-policy
description: "비밀값(keystore, 비밀번호, API 키)은 커밋·APK에 넣지 않는다 — 10/24의 \"API 키 앱 내장 허용\" 결정을 10/25 명세가 대체"
metadata:
  node_type: memory
  type: feedback
  originSessionId: a0acef44-095f-4903-afed-69862aa322b3
  modified: 2026-10-25T15:16:00.467Z
---

keystore, 비밀번호, API 키는 절대 커밋하지 않고 `.env.example` 같은 예시 파일만 둔다. Supabase service_role 키는 APK에도 웹에도 넣지 않는다. 첫 push 전과 제출 전에 커밋에 비밀값이 섞였는지 검사하고 결과를 보고한다.

**Why:** 2026-10-24 이전 세션에서는 사용자가 "외부 LLM API 키는 앱에 넣어도 된다"고 했지만, 2026-10-25 DriveLog 명세(0번, 8번)가 이를 명시적으로 금지한다. 더 최근의 지시를 따른다. 공모전 GitHub 제출 시 저장소가 심사위원에게 공개될 수 있다.

**How to apply:** 공개 가능한 키(Supabase anon key 등)도 local.properties/.env → BuildConfig로 주입하고 저장소에는 예시만 둔다. 관련: [[drivelog-project]]

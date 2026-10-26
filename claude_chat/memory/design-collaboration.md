---
name: design-collaboration
description: "디자인 담당은 사용자 claude.ai 앱의 \"Claude 앱 개발 연결\" 채팅. design/ 폴더로 주고받고, 개발 쪽 질문은 design/REQUESTS.md에 적는다"
metadata:
  node_type: memory
  type: reference
  originSessionId: a0acef44-095f-4903-afed-69862aa322b3
  modified: 2026-10-26T05:09:30.283Z
---

- 디자인 담당 Claude는 사용자의 claude.ai 앱에 있는 **"Claude 앱 개발 연결"** 이라는 채팅이다 (2026-10-26 사용자 설명). 그쪽이 저장소의 `design/` 폴더(토큰, 화면 명세, 목업, CHANGELOG)를 갱신한다.
- 디자인 쪽이 개발 쪽(Claude Code)에 할 일을 보낼 때는 사용자가 프롬프트를 붙여 넣어 전달한다.
- 규칙은 저장소 루트 `CLAUDE.md`에 있다 (design/ 읽기 전용, `drivelog_` 토큰만 사용, 명세 먼저 읽기, CHANGELOG 확인). 질문·요청은 `design/REQUESTS.md`에만 적는다.
- 2026-10-26 기준 토큰·명세는 아직 없다 ("콘셉트 확정 대기"). 사용자는 5단계를 "구조 먼저" 진행하라고 했다.

관련: [[drivelog-project]]

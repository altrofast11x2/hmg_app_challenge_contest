---
name: no-browser-on-desktop1
description: 사용자가 쓰는 데스크톱1에 브라우저 탭·창을 띄우지 말 것. Virtual Console 조작은 HTTP API(curl)로
metadata:
  node_type: memory
  type: feedback
  originSessionId: a0acef44-095f-4903-afed-69862aa322b3
  modified: 2026-10-26T05:03:24.682Z
---

브라우저 자동화(Chrome 확장)로 탭이나 창을 새로 열지 않는다. Virtual Console 조작이 필요하면 HTTP API로 한다 (방법은 [[pleos-dev-environment]]).

**Why:** 2026-10-26 4단계 검증 중 내가 Chrome 확장으로 Virtual Console 탭을 열자, 사용자가 "데스크톱1은 내가 쓰고 있음, 테스트 데스크톱 2에 켜져 있음, 그걸로 부탁함"이라고 했다. 사용자가 데스크톱 2에 열어 둔 탭은 확장의 탭 그룹 밖이라 제어할 수 없다.

**How to apply:** 화면 조작이 꼭 필요하면(HTTP API로 안 되는 경우) 먼저 사용자에게 물어본다. 에뮬레이터 화면 확인은 `adb exec-out screencap`, 탭은 `adb shell input tap`으로 한다.

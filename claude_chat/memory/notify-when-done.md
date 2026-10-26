---
name: notify-when-done
description: 작업(단계)이 끝나 사용자 확인이 필요할 때 Windows msg 명령으로 이 PC에 알림 창을 띄워 부른다
metadata:
  node_type: memory
  type: feedback
  originSessionId: a0acef44-095f-4903-afed-69862aa322b3
  modified: 2026-10-26T04:46:45.548Z
---

긴 작업이 끝나서 사용자에게 보고하거나 확인받을 차례가 되면, 마지막 보고 직전에 이 PC에 알림 창을 띄운다: `msg <사용자명> "자동 운행기록부: ... 완료, 확인해 주세요"` (PowerShell/Bash에서 `msg $env:USERNAME ...`, 실패하면 PowerShell MessageBox).

**Why:** 2026-10-26 4단계 진행 중 사용자가 "작업완료되면 이 컴퓨터에 msg 띄워서 불러"라고 했다. 작업하는 동안 자리를 비우기 때문이다.

**How to apply:** 단계 완료 보고, 사용자의 조작(Virtual Console 등)이 필요한 시점, push 확인 요청 등 사용자를 기다려야 할 때 띄운다. 짧은 질답에는 띄우지 않는다. 관련: [[user-profile]]

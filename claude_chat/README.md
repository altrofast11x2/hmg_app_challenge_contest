# claude_chat: Claude Code 대화 기록

이 폴더에는 Claude Code와 나눈 개발 대화와, 다른 컴퓨터나 새 세션에서 이어가는 데 필요한 파일이 들어 있다.

| 경로 | 내용 |
|---|---|
| `SPEC.md` | 프로젝트 명세 원문과 추가 지시. **가장 먼저 읽을 문서** |
| `HANDOFF.md` | 지금 어느 단계인지, 다음에 할 일 |
| `transcript/` | 대화 원본 기록(`.jsonl`)과 세션 ID |
| `memory/` | Claude Code가 이 프로젝트에 대해 기억하는 내용 |
| `export.ps1` | 최신 대화와 메모리를 이 폴더로 다시 복사 |
| `restore.ps1` | 다른 컴퓨터에서 대화와 메모리를 Claude Code 기록 폴더로 복원 |

이전 세션(2026-10-22~24, 아이디어 탐색 단계)의 기록은 `altrofast11x2_backup` 브랜치의 `claude_chat/`에 남아 있다.

## 최신 대화로 갱신하기

```powershell
powershell -ExecutionPolicy Bypass -File claude_chat\export.ps1
git add claude_chat; git commit -m "대화 기록 갱신"
```

대화는 계속 쌓이므로, 컴퓨터를 바꾸기 직전에 한 번 더 내보내야 마지막 대화까지 옮겨진다.

## 다른 컴퓨터에서 이어가기

1. `git clone https://github.com/altrofast11x2/hmg_app_challenge_contest.git DriveLog` → `cd DriveLog` → `git checkout altrofast11x2`
2. `powershell -ExecutionPolicy Bypass -File claude_chat\restore.ps1`
3. 스크립트가 알려 주는 `claude --resume <세션 ID>`로 이어간다. 잘 안 되면 새 대화에서 "claude_chat/SPEC.md와 HANDOFF.md 읽고 이어서 하자"라고 하면 된다.

## 주의

- 대화 원본에는 작업 중 출력된 경로, 로그, 스크린샷이 모두 들어 있다. 공모전 제출 전에(심사용 계정 초대나 공개 전환 전에) 이 폴더를 저장소에서 뺄지 결정한다.

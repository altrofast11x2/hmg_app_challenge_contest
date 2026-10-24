# claude_chat: 다른 컴퓨터에서 Claude Code 대화 이어가기

이 폴더에는 Claude Code와 나눈 개발 대화를 다른 컴퓨터에서 이어가는 데 필요한 파일이 들어 있다.

| 경로 | 내용 |
|---|---|
| `HANDOFF.md` | 지금까지의 결정·발견·상태 요약. **가장 먼저 읽을 문서** |
| `transcript/` | 대화 원본 기록(`.jsonl`)과 세션 ID |
| `memory/` | Claude Code가 이 프로젝트에 대해 기억하는 내용 |
| `patches/stash-0.patch` | 원래 컴퓨터의 git stash에 보관한 변경 (테스트 권한, Car API 설정) |
| `backup/diagnostics/` | 에뮬레이터 SDK 진단 화면 코드 백업 |
| `export.ps1` | (원래 컴퓨터) 최신 대화·메모리·stash를 이 폴더로 다시 내보내기 |
| `restore.ps1`, `restore.sh` | (다른 컴퓨터) 대화·메모리를 Claude Code 기록 폴더로 복원하기 |

## 다른 컴퓨터에서 이어가기

1. 저장소를 받고 작업 브랜치로 바꾼다.
   ```bash
   git clone https://github.com/altrofast11x2/hmg_app_challenge_contest.git
   cd hmg_app_challenge_contest
   git checkout altrofast11x2
   ```
   가능하면 원래와 같은 경로(`C:\dev\hmg_app_challenge_contest`)에 받는다. 경로가 달라도 복원 스크립트가 알맞은 폴더를 계산한다.
2. 복원 스크립트를 실행한다.
   - Windows: `powershell -ExecutionPolicy Bypass -File claude_chat\restore.ps1`
   - Mac/Linux: `bash claude_chat/restore.sh`
3. 스크립트가 알려 주는 명령으로 대화를 이어간다: `claude --resume <세션 ID>`
   - 복원이 잘 안 되면 새 대화에서 **"claude_chat/HANDOFF.md 읽고 이어서 하자"** 라고 하면 된다.
4. 개발 환경(JDK 17, Android Studio, Pleos 에뮬레이터)은 컴퓨터마다 따로 설치해야 한다. 설치 방법은 HANDOFF.md 3번에 있다.

## 원래 컴퓨터에서 최신 대화로 갱신하기

```powershell
powershell -ExecutionPolicy Bypass -File claude_chat\export.ps1
git add claude_chat; git commit -m "대화 기록 갱신"; git push
```

대화가 계속 쌓이므로 **컴퓨터를 바꾸기 직전에** 한 번 더 내보내야 마지막 대화까지 옮겨진다.

## 주의

- 대화 원본에는 작업 중 출력된 경로·로그·스크린샷이 모두 들어 있다. 저장소를 심사위원이나 다른 사람에게 공유하기 전에 이 폴더를 뺄지 결정한다.

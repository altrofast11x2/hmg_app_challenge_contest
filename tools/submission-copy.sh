#!/usr/bin/env bash
# 심사용 깨끗한 사본 저장소를 만들거나 갱신한다 (이 개발 저장소의 HEAD 기준, 커밋 하나).
#
# 공모전 접수양식: "공유 링크와 첨부자료에도 개인정보·계정정보·개발자 실명이 노출되지 않도록" (블라인드 심사).
# 그래서 사본에는 개발 이력(작성자 이메일)과 AI 협업 기록(claude_chat/), 개발용 설정을 넣지 않는다.
# 개발은 이 저장소에서 계속하고, 제출 전에 이 스크립트로 사본을 다시 올린다.
#
# 사용: bash tools/submission-copy.sh [사본 저장소 주소]
set -euo pipefail

REPO_URL=${1:-https://github.com/altrofast11x2/AltroStudio_DriveLog.git}
AUTHOR_NAME="AltroStudio"
AUTHOR_EMAIL="230317413+altrofast11x2@users.noreply.github.com"

SOURCE=$(git rev-parse --short HEAD)
if [ -n "$(git status --porcelain)" ]; then
  echo "커밋하지 않은 변경이 있습니다. 먼저 커밋하세요." >&2
  exit 1
fi

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
git archive --format=tar HEAD | tar -x -C "$WORK"

# 사본에서 빼는 것: AI 협업 기록, 개발용 설정·워크플로(서버 깨우기는 개발 저장소에서 돈다), 이 스크립트, 내부 제출 점검표
rm -rf "$WORK/claude_chat" "$WORK/.github" "$WORK/tools" "$WORK/CLAUDE.md" "$WORK/.mcp.json" \
  "$WORK/docs/submission-checklist.md"
sed -i '/^| `claude_chat\/` |/d' "$WORK/README.md"
sed -i 's/, 제출 점검표는 \[docs\/submission-checklist.md\](docs\/submission-checklist.md)에 있습니다\./에 있습니다./' "$WORK/README.md"

# 남은 파일에 빼야 할 것이 없는지 마지막으로 확인
if grep -rIl --exclude-dir=.git -e "parentUuid" -e "sb_secret_[A-Za-z0-9]\{12,\}" "$WORK" | grep -v "web/scripts" ; then
  echo "사본에 대화 기록이나 관리 키로 보이는 내용이 있습니다. 확인하세요." >&2
  exit 1
fi

cd "$WORK"
git init -q -b main
git add -A
GIT_AUTHOR_NAME="$AUTHOR_NAME" GIT_AUTHOR_EMAIL="$AUTHOR_EMAIL" \
GIT_COMMITTER_NAME="$AUTHOR_NAME" GIT_COMMITTER_EMAIL="$AUTHOR_EMAIL" \
  git commit -q -m "자동 운행기록부(DriveLog) 제출본 $(date +%Y-%m-%d)"
git remote add origin "$REPO_URL"
git push -q -f origin main
echo "올림: $REPO_URL (원본 $SOURCE, 파일 $(git ls-files | wc -l)개)"

#!/usr/bin/env bash
# 다른 Mac/Linux 컴퓨터에서 claude_chat에 담긴 대화 기록·메모리를 Claude Code 기록 폴더로 복원한다.
# 사용: 저장소를 clone 한 뒤 bash claude_chat/restore.sh
set -euo pipefail

chat_dir="$(cd "$(dirname "$0")" && pwd)"
repo_root="$(dirname "$chat_dir")"
# Claude Code는 저장소 경로의 영문·숫자 외 문자를 '-'로 바꾼 이름의 폴더에 기록을 둔다
slug="$(printf '%s' "$repo_root" | sed 's/[^A-Za-z0-9]/-/g')"
project_dir="$HOME/.claude/projects/$slug"

if [ -d "$project_dir/memory" ]; then
  backup="$project_dir/memory.bak-$(date +%Y%m%d-%H%M%S)"
  cp -R "$project_dir/memory" "$backup"
  echo "기존 메모리 백업: $backup"
fi

mkdir -p "$project_dir/memory"
cp "$chat_dir"/transcript/*.jsonl "$project_dir/"
cp "$chat_dir"/memory/*.md "$project_dir/memory/"
session_id="$(tr -d '\r\n ' < "$chat_dir/transcript/SESSION_ID.txt")"

echo "복원 완료: $project_dir"
echo "저장소 폴더($repo_root)에서 다음 명령으로 이어서 대화하세요:"
echo "  claude --resume $session_id"

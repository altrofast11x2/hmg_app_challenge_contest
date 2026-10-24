# 이 컴퓨터의 Claude Code 대화 기록·메모리·보관 중인 변경(stash)을 claude_chat 폴더로 내보낸다.
# 사용: powershell -ExecutionPolicy Bypass -File claude_chat\export.ps1 [-SessionId <세션 ID>]
# 세션 ID를 생략하면 가장 최근 대화를 내보낸다.
param([string]$SessionId = "")

$ErrorActionPreference = "Stop"
$chatDir = $PSScriptRoot
$repoRoot = Split-Path $chatDir -Parent
# Claude Code는 저장소 경로의 영문·숫자 외 문자를 '-'로 바꾼 이름의 폴더에 기록을 둔다
$slug = $repoRoot -replace '[^A-Za-z0-9]', '-'
$projectDir = Join-Path $env:USERPROFILE ".claude\projects\$slug"

if (-not (Test-Path $projectDir)) { throw "Claude Code 기록 폴더가 없습니다: $projectDir" }
if ($SessionId -eq "") {
    $SessionId = (Get-ChildItem $projectDir -Filter *.jsonl | Sort-Object LastWriteTime -Descending | Select-Object -First 1).BaseName
}

New-Item -ItemType Directory -Force "$chatDir\transcript", "$chatDir\memory", "$chatDir\patches" | Out-Null
Copy-Item "$projectDir\$SessionId.jsonl" "$chatDir\transcript\" -Force
Copy-Item "$projectDir\memory\*.md" "$chatDir\memory\" -Force
Set-Content "$chatDir\transcript\SESSION_ID.txt" $SessionId -Encoding ascii

# git stash는 GitHub에 올라가지 않으므로 패치 파일로 남긴다 (cmd 리다이렉션으로 인코딩 변형 방지)
Push-Location $repoRoot
try {
    if (git stash list) {
        cmd /c "git stash show -p --include-untracked stash@{0} > `"$chatDir\patches\stash-0.patch`""
        git stash list | Select-Object -First 1 | Set-Content "$chatDir\patches\stash-0.txt" -Encoding utf8
    }
} finally {
    Pop-Location
}

Write-Host "내보내기 완료: 세션 $SessionId"
Write-Host "다른 컴퓨터로 옮기려면 claude_chat 폴더를 커밋하고 push 하세요."

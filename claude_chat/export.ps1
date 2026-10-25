# 이 컴퓨터의 Claude Code 대화 기록과 메모리를 claude_chat 폴더로 복사한다.
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

New-Item -ItemType Directory -Force "$chatDir\transcript", "$chatDir\memory" | Out-Null
Copy-Item "$projectDir\$SessionId.jsonl" "$chatDir\transcript\" -Force
if (Test-Path "$projectDir\memory\*.md") { Copy-Item "$projectDir\memory\*.md" "$chatDir\memory\" -Force }
Set-Content "$chatDir\transcript\SESSION_ID.txt" $SessionId -Encoding ascii

Write-Host "내보내기 완료: 세션 $SessionId"
Write-Host "claude_chat 폴더를 커밋하고 push 하면 다른 컴퓨터에서 이어갈 수 있습니다."

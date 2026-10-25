# 다른 Windows 컴퓨터에서 claude_chat에 담긴 대화 기록과 메모리를 Claude Code 기록 폴더로 복원한다.
# 사용: 저장소를 clone 한 뒤 powershell -ExecutionPolicy Bypass -File claude_chat\restore.ps1

$ErrorActionPreference = "Stop"
$chatDir = $PSScriptRoot
$repoRoot = Split-Path $chatDir -Parent
$slug = $repoRoot -replace '[^A-Za-z0-9]', '-'
$projectDir = Join-Path $env:USERPROFILE ".claude\projects\$slug"

# 이 컴퓨터에 이미 메모리가 있으면 덮어쓰기 전에 백업
if (Test-Path "$projectDir\memory") {
    $backup = "$projectDir\memory.bak-" + (Get-Date -Format "yyyyMMdd-HHmmss")
    Copy-Item "$projectDir\memory" $backup -Recurse
    Write-Host "기존 메모리 백업: $backup"
}

New-Item -ItemType Directory -Force "$projectDir\memory" | Out-Null
Copy-Item "$chatDir\transcript\*.jsonl" $projectDir -Force
Copy-Item "$chatDir\memory\*.md" "$projectDir\memory\" -Force
$sessionId = (Get-Content "$chatDir\transcript\SESSION_ID.txt").Trim()

Write-Host "복원 완료: $projectDir"
Write-Host "저장소 폴더($repoRoot)에서 다음 명령으로 이어서 대화하세요:"
Write-Host "  claude --resume $sessionId"

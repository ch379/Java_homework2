$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runtimeRoot = Join-Path $projectRoot '.runtime'
$pidFile = Join-Path $runtimeRoot 'tomcat.pid'
if (-not (Test-Path -LiteralPath $pidFile)) { Write-Output 'No managed Tomcat PID found.'; return }
$savedPid = [int](Get-Content -LiteralPath $pidFile)
$process = Get-CimInstance Win32_Process -Filter "ProcessId=$savedPid" -ErrorAction SilentlyContinue
if ($process) {
    if (-not $process.CommandLine.Contains($runtimeRoot)) { throw 'PID belongs to a different process; refusing to stop it.' }
    Stop-Process -Id $savedPid
}
Remove-Item -LiteralPath $pidFile
Write-Output 'Library Tomcat stopped.'

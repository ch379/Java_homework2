$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $env:LIBRARY_DB_PASSWORD) { throw 'Set LIBRARY_DB_PASSWORD to your MySQL password first.' }
$previousUrl = $env:LIBRARY_DB_URL
$previousIntegration = $env:LIBRARY_INTEGRATION
Push-Location $projectRoot
try {
    $env:MYSQL_PWD = $env:LIBRARY_DB_PASSWORD
    $count = & mysql -u root -N -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='libraryweb2026_test'"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot connect to local MySQL.' }
    if ([int]$count -eq 0) {
        & python scripts/restore_backup.py ../LibraryDB.nb3 --database libraryweb2026_test
        if ($LASTEXITCODE -ne 0) { throw 'Cannot restore test database.' }
    }
    $env:LIBRARY_DB_URL = 'jdbc:mysql://localhost:3306/libraryweb2026_test?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
    $env:LIBRARY_INTEGRATION = 'true'
    & mvn.cmd test
    if ($LASTEXITCODE -ne 0) { throw 'Integration tests failed.' }
} finally {
    $env:MYSQL_PWD = $null
    $env:LIBRARY_DB_URL = $previousUrl
    $env:LIBRARY_INTEGRATION = $previousIntegration
    Pop-Location
}

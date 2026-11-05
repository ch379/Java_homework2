param([switch]$SkipBuild, [int]$Port = 8080)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runtimeRoot = Join-Path $projectRoot '.runtime'
$tomcatVersion = '10.1.61'
$tomcatRoot = Join-Path $runtimeRoot "apache-tomcat-$tomcatVersion"
$configFile = Join-Path $projectRoot 'config\db.local.properties'
if (-not (Test-Path -LiteralPath $configFile)) { throw 'Copy config/db.example.properties to config/db.local.properties and enter your MySQL credentials first.' }
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { throw 'JAVA_HOME must point to a JDK 21 installation.' }
if (-not (Test-Path -LiteralPath $tomcatRoot)) {
    New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null
    $archive = Join-Path $runtimeRoot "apache-tomcat-$tomcatVersion.zip"
    $baseUrl = "https://archive.apache.org/dist/tomcat/tomcat-10/v$tomcatVersion/bin/apache-tomcat-$tomcatVersion.zip"
    Invoke-WebRequest -Uri $baseUrl -OutFile $archive
    $checksumText = (Invoke-WebRequest -Uri "$baseUrl.sha512").Content
    $expectedHash = ($checksumText -split '\s+')[0]
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash -ne $expectedHash) { throw 'Tomcat archive checksum mismatch.' }
    Expand-Archive -LiteralPath $archive -DestinationPath $runtimeRoot
}
if (-not $SkipBuild) {
    Push-Location $projectRoot
    try { & mvn.cmd package; if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' } } finally { Pop-Location }
}
$warFile = Join-Path $projectRoot 'target\LibraryWeb2026.war'
if (-not (Test-Path -LiteralPath $warFile)) { throw 'WAR file not found. Run mvn package first.' }
[Environment]::SetEnvironmentVariable('JAVA_HOME',$env:JAVA_HOME,'User')
[Environment]::SetEnvironmentVariable('CATALINA_HOME',$tomcatRoot,'User')
$userPath = [string][Environment]::GetEnvironmentVariable('Path','User')
if (($userPath -split ';') -notcontains '%CATALINA_HOME%\bin') {
    [Environment]::SetEnvironmentVariable('Path',($userPath.TrimEnd(';') + ';%CATALINA_HOME%\bin'),'User')
}
$env:CATALINA_HOME = $tomcatRoot
# Make bin/startup.bat work as well as this script.
$setenv = '@echo off' + [Environment]::NewLine + 'set "JAVA_OPTS=%JAVA_OPTS% -Dfile.encoding=UTF-8 -Djava.awt.headless=true -Duser.timezone=Asia/Shanghai -Dlibrary.config=' + $configFile + '"'
[IO.File]::WriteAllText((Join-Path $tomcatRoot 'bin\setenv.bat'),$setenv,[Text.Encoding]::ASCII)
$serverXml = Join-Path $tomcatRoot 'conf\server.xml'
[xml]$server = Get-Content -LiteralPath $serverXml
$connector = $server.Server.Service.Connector | Where-Object { $_.protocol -eq 'HTTP/1.1' }
$connector.port = [string]$Port
$server.Save($serverXml)
$pidFile = Join-Path $runtimeRoot 'tomcat.pid'
$running = $null
if (Test-Path -LiteralPath $pidFile) {
    $savedPid = [int](Get-Content -LiteralPath $pidFile)
    $candidate = Get-CimInstance Win32_Process -Filter "ProcessId=$savedPid" -ErrorAction SilentlyContinue
    if ($candidate -and $candidate.CommandLine.Contains($tomcatRoot)) { $running = $candidate }
}
Copy-Item -LiteralPath $warFile -Destination (Join-Path $tomcatRoot 'webapps\LibraryWeb2026.war') -Force
if (-not $running) {
    $javaArgs = @('-Dfile.encoding=UTF-8','-Djava.awt.headless=true','-Duser.timezone=Asia/Shanghai',
        ('-Dlibrary.config="' + $configFile + '"'), ('-Dcatalina.home="' + $tomcatRoot + '"'), ('-Dcatalina.base="' + $tomcatRoot + '"'),
        '-classpath', ('"' + $tomcatRoot + '\bin\bootstrap.jar;' + $tomcatRoot + '\bin\tomcat-juli.jar"'), 'org.apache.catalina.startup.Bootstrap','start')
    $process = Start-Process -FilePath (Join-Path $env:JAVA_HOME 'bin\java.exe') -ArgumentList $javaArgs -WorkingDirectory $tomcatRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $tomcatRoot 'logs\console.out.log') -RedirectStandardError (Join-Path $tomcatRoot 'logs\console.err.log') -PassThru
    Set-Content -LiteralPath $pidFile -Value $process.Id
}
$url = "http://localhost:$Port/LibraryWeb2026/"
$started = $false
for ($attempt=0; $attempt -lt 30; $attempt++) {
    try { $response = Invoke-WebRequest -Uri ($url + 'api/session') -UseBasicParsing -TimeoutSec 2; if ($response.StatusCode -eq 200) { $started = $true; break } } catch {}
    Start-Sleep -Milliseconds 700
}
if (-not $started) { throw "Application not ready. Inspect $tomcatRoot\logs\console.err.log" }
Write-Output "Library is ready: $url"
Write-Output "WAR: $warFile"
Write-Output "CATALINA_HOME: $tomcatRoot"
Write-Output 'For LAN access, use this computer IPv4 address in place of localhost.'

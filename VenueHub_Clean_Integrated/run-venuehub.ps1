$ErrorActionPreference = 'Stop'

# Known Java/Maven paths on the machine used to prepare this project.
# If your paths differ, update these two values or configure JAVA_HOME and Maven in Windows.
$jdkPath = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$mavenCmd = Join-Path $env:USERPROFILE 'Documents\apache-maven-3.10.0\bin\mvn.cmd'

if (Test-Path (Join-Path $jdkPath 'bin\javac.exe')) {
    $env:JAVA_HOME = $jdkPath
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

Write-Host "`n--- Java compiler ---" -ForegroundColor Cyan
javac -version

if (Test-Path $mavenCmd) {
    Write-Host "`n--- Maven Java runtime ---" -ForegroundColor Cyan
    & $mavenCmd -version
    if ($LASTEXITCODE -ne 0) { throw 'Maven version check failed.' }
    Write-Host "`n--- Starting VenueHub ---" -ForegroundColor Cyan
    & $mavenCmd org.springframework.boot:spring-boot-maven-plugin:3.5.6:run
    exit $LASTEXITCODE
}

$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if ($null -ne $mvn) {
    Write-Host "`n--- Starting VenueHub ---" -ForegroundColor Cyan
    & mvn org.springframework.boot:spring-boot-maven-plugin:3.5.6:run
    exit $LASTEXITCODE
}

throw "Maven was not found. Update the Maven path in run-venuehub.ps1 or install Maven."

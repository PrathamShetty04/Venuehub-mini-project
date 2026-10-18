$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root
$port = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($port) {
    $pids = @($port | Select-Object -ExpandProperty OwningProcess -Unique)
    Write-Host "Port 8080 is already in use by process ID(s): $($pids -join ', ')." -ForegroundColor Yellow
    $choice = Read-Host 'Stop the process(es) and start this VenueHub version? Type Y to continue'
    if ($choice -match '^(Y|YES)$') {
        foreach ($processId in $pids) {
            try { Stop-Process -Id $processId -Force -ErrorAction Stop; Write-Host "Stopped process $processId." }
            catch { Write-Host "Could not stop process $processId. Close the application using port 8080 and run this launcher again." -ForegroundColor Red; exit 1 }
        }
        Start-Sleep -Seconds 2
    } else {
        Start-Process 'http://localhost:8080/'
        Write-Host 'Opened the existing server. To run this extracted version, close the process using port 8080 and relaunch.' -ForegroundColor Yellow
        exit 0
    }
}
$runner = Join-Path $root 'run-venuehub.ps1'
$arguments = "-NoExit -NoProfile -ExecutionPolicy Bypass -File `"$runner`""
Start-Process -FilePath 'powershell.exe' -ArgumentList $arguments
Write-Host 'Starting VenueHub. Waiting for the server...' -ForegroundColor Cyan
for ($i = 0; $i -lt 90; $i++) {
    Start-Sleep -Seconds 2
    try {
        $response = Invoke-WebRequest 'http://localhost:8080/' -TimeoutSec 2 -UseBasicParsing
        if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
            Start-Process 'http://localhost:8080/'
            Write-Host 'VenueHub is ready and opened in your browser.' -ForegroundColor Green
            exit 0
        }
    } catch { }
}
Write-Host 'VenueHub did not become ready within 3 minutes. Check the other PowerShell window for errors.' -ForegroundColor Red
exit 1

$ErrorActionPreference = 'Stop'
$uri = 'http://localhost:8080/api/venues'
Write-Host "Checking $uri ..." -ForegroundColor Cyan
$result = Invoke-RestMethod -Uri $uri -Method Get -TimeoutSec 10
if (-not $result.success) {
    throw "API returned an error: $($result.message)"
}
Write-Host "API is responding. Venue records returned: $($result.data.Count)" -ForegroundColor Green
$result.data | Select-Object id, name, city, price, active | Format-Table -AutoSize

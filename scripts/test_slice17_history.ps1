<#
.SYNOPSIS
  End-to-end automated integration test for Slice 17: Client Trip History & Billing.
#>
$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  TaaSim - Slice 17 Client Trip & Billing History Test    " -ForegroundColor Cyan
Write-Host "=========================================================="

# 1. Health Checks
Write-Host "`n1. Checking Microservices Health..." -ForegroundColor Yellow
$services = @("8080", "8081", "8082", "8083", "8086")
foreach ($port in $services) {
    try {
        $h = Invoke-RestMethod -Uri "http://localhost:$port/actuator/health" -Method Get
        Write-Host "  Port $port Status: $($h.status)" -ForegroundColor Green
    } catch {
        Write-Host "  [FAIL] Port $port is unreachable" -ForegroundColor Red
        exit 1
    }
}

# Clear stale Redis driver locations to ensure clean matching
docker exec taasim-redis redis-cli DEL drivers:available | Out-Null
Write-Host "  [OK] Cleaned Redis driver locations for test isolation" -ForegroundColor Gray

# 2. Register Client A, Client B, and Driver
Write-Host "`n2. Registering Test Users (Client A, Client B, Driver)..." -ForegroundColor Yellow

$driverEmail = "driver_17_$([Guid]::NewGuid().ToString().Substring(0,6))@test.com"
$clientAEmail = "clientA_17_$([Guid]::NewGuid().ToString().Substring(0,6))@test.com"
$clientBEmail = "clientB_17_$([Guid]::NewGuid().ToString().Substring(0,6))@test.com"

# Driver
$driverReg = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" `
    -Body (@{ email = $driverEmail; password = "password123"; fullName = "Driver Slice 17"; role = "DRIVER" } | ConvertTo-Json)
$driverLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" `
    -Body (@{ email = $driverEmail; password = "password123" } | ConvertTo-Json)
$driverToken = $driverLogin.accessToken.Trim()
$driverId = "taxi_17_" + [Guid]::NewGuid().ToString().Substring(0,6)
Write-Host "  Driver registered: $driverId ($driverEmail)" -ForegroundColor Green

# Client A
$clientAReg = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" `
    -Body (@{ email = $clientAEmail; password = "password123"; fullName = "Client A"; role = "CLIENT" } | ConvertTo-Json)
$clientALogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" `
    -Body (@{ email = $clientAEmail; password = "password123" } | ConvertTo-Json)
$clientAToken = $clientALogin.accessToken.Trim()
$clientAId = $clientAReg.id
Write-Host "  Client A registered: $clientAId ($clientAEmail)" -ForegroundColor Green

# Client B
$clientBReg = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" `
    -Body (@{ email = $clientBEmail; password = "password123"; fullName = "Client B"; role = "CLIENT" } | ConvertTo-Json)
$clientBLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" `
    -Body (@{ email = $clientBEmail; password = "password123" } | ConvertTo-Json)
$clientBToken = $clientBLogin.accessToken.Trim()
$clientBId = $clientBReg.id
Write-Host "  Client B registered: $clientBId ($clientBEmail)" -ForegroundColor Green

# 3. Position Driver & Client A requests a Ride
Write-Host "`n3. Positioning Driver & Requesting Ride for Client A..." -ForegroundColor Yellow

$driverGps = @{
    driverId = $driverId
    lat = 33.5735
    lon = -7.5895
    speed = 0.0
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/location" -Method Post `
    -Headers @{ Authorization = "Bearer $driverToken" } -ContentType "application/json" -Body $driverGps | Out-Null
Write-Host "  Driver location pinged at (33.5735, -7.5895)" -ForegroundColor Gray

$tripReq = @{
    originZone = 5
    destinationZone = 12
    originLat = 33.5735
    originLon = -7.5895
    destinationLat = 33.5900
    destinationLon = -7.6100
} | ConvertTo-Json

$tripResp = Invoke-RestMethod -Uri "http://localhost:8080/api/trips/request" -Method Post `
    -Headers @{ Authorization = "Bearer $clientAToken" } -ContentType "application/json" -Body $tripReq

$tripId = $tripResp.tripId
Write-Host "  [OK] Client A requested Trip: $tripId" -ForegroundColor Green

# 4. Driver accepts, starts, moves, and completes trip
Write-Host "`n4. Driver Lifecycle: Accept -> Start -> Telemetry -> Complete..." -ForegroundColor Yellow

$accepted = $false
for ($i = 1; $i -le 10; $i++) {
    Start-Sleep -Seconds 1
    try {
        Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/trips/$driverId/accept" -Method Put `
            -Headers @{ Authorization = "Bearer $driverToken" } | Out-Null
        $accepted = $true
        Write-Host "  [OK] Trip accepted by driver $driverId" -ForegroundColor Green
        break
    } catch {
        Write-Host "  Waiting for match event (attempt $i)..." -ForegroundColor Gray
    }
}

if (-not $accepted) {
    Write-Host "  [FAIL] Trip not accepted within timeout" -ForegroundColor Red
    exit 1
}

# Start ride
Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/trips/$driverId/start" -Method Put `
    -Headers @{ Authorization = "Bearer $driverToken" } | Out-Null
Write-Host "  [OK] Ride started!" -ForegroundColor Green

# GPS movement
$waypoints = @(
    @{ lat = 33.5780; lon = -7.5950; speed = 30.0 },
    @{ lat = 33.5840; lon = -7.6020; speed = 35.0 },
    @{ lat = 33.5900; lon = -7.6100; speed = 25.0 }
)

foreach ($wp in $waypoints) {
    Start-Sleep -Milliseconds 400
    $wpBody = @{ driverId = $driverId; lat = $wp.lat; lon = $wp.lon; speed = $wp.speed } | ConvertTo-Json
    Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/location" -Method Post `
        -Headers @{ Authorization = "Bearer $driverToken" } -ContentType "application/json" -Body $wpBody | Out-Null
}

Start-Sleep -Seconds 1

# Complete ride
Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/trips/$driverId/complete" -Method Put `
    -Headers @{ Authorization = "Bearer $driverToken" } | Out-Null
Write-Host "  [OK] Trip marked COMPLETED! Kafka event dispatched." -ForegroundColor Green

# 5. Poll Billing Record in PostgreSQL
Write-Host "`n5. Polling Billing Record for Trip $tripId..." -ForegroundColor Yellow
$billingFound = $false
for ($i = 1; $i -le 10; $i++) {
    Start-Sleep -Seconds 1
    try {
        $billResp = Invoke-RestMethod -Uri "http://localhost:8080/api/billing/trips/$tripId" -Method Get `
            -Headers @{ Authorization = "Bearer $clientAToken" }
        if ($billResp -ne $null -and $billResp.tripId -eq $tripId) {
            $billingFound = $true
            Write-Host "  [OK] Billing record ready: Total Fare = $($billResp.totalFare) $($billResp.currency)" -ForegroundColor Green
            break
        }
    } catch {
        Write-Host "  Waiting for billing calculation (attempt $i)..." -ForegroundColor Gray
    }
}

if (-not $billingFound) {
    Write-Host "  [FAIL] Billing record not created in time" -ForegroundColor Red
    exit 1
}

# 6. Verify Client A Trip History & Billing History
Write-Host "`n6. Verifying Client A Trip & Billing History Endpoints..." -ForegroundColor Yellow

$tripsHistoryA = Invoke-RestMethod -Uri "http://localhost:8080/api/trips/history?limit=10" -Method Get `
    -Headers @{ Authorization = "Bearer $clientAToken" }

Write-Host "  Client A trips count: $($tripsHistoryA.count)"
$foundTripInHistory = $tripsHistoryA.trips | Where-Object { $_.tripId -eq $tripId }
if ($foundTripInHistory) {
    Write-Host "  [SUCCESS] Trip $tripId present in Client A trip history!" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Trip $tripId NOT found in Client A trip history" -ForegroundColor Red
    exit 1
}

$billingHistoryA = Invoke-RestMethod -Uri "http://localhost:8080/api/billing/history?page=0&size=10" -Method Get `
    -Headers @{ Authorization = "Bearer $clientAToken" }

Write-Host "  Client A billing records: $($billingHistoryA.totalElements) (totalPages: $($billingHistoryA.totalPages), page: $($billingHistoryA.page))"
$foundBillInHistory = $billingHistoryA.records | Where-Object { $_.tripId -eq $tripId }
if ($foundBillInHistory) {
    Write-Host "  [SUCCESS] Billing record for trip $tripId present in Client A billing history!" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Billing record NOT found in Client A billing history" -ForegroundColor Red
    exit 1
}

# 7. Verify Client B Tenant Isolation & Authorization Enforcement
Write-Host "`n7. Verifying Tenant Isolation (Client B cannot see Client A's data)..." -ForegroundColor Yellow

$tripsHistoryB = Invoke-RestMethod -Uri "http://localhost:8080/api/trips/history?limit=10" -Method Get `
    -Headers @{ Authorization = "Bearer $clientBToken" }
Write-Host "  Client B trip count: $($tripsHistoryB.count) (Expected: 0)"

$billingHistoryB = Invoke-RestMethod -Uri "http://localhost:8080/api/billing/history?page=0&size=10" -Method Get `
    -Headers @{ Authorization = "Bearer $clientBToken" }
Write-Host "  Client B billing totalElements: $($billingHistoryB.totalElements) (Expected: 0)"

if ($tripsHistoryB.count -eq 0 -and $billingHistoryB.totalElements -eq 0) {
    Write-Host "  [SUCCESS] Client B history is empty as expected!" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Tenant data leak detected for Client B" -ForegroundColor Red
    exit 1
}

# Client B attempts to access Client A's fare breakdown
Write-Host "  Testing Client B unauthorized access to Client A's trip fare breakdown..." -ForegroundColor Gray
$forbidden = $false
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/billing/trips/$tripId" -Method Get `
        -Headers @{ Authorization = "Bearer $clientBToken" }
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 403) {
        $forbidden = $true
        Write-Host "  [SUCCESS] Access correctly denied with 403 Forbidden!" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] Expected 403 but got: $($_.Exception.Message)" -ForegroundColor Red
        exit 1
    }
}

if (-not $forbidden) {
    Write-Host "  [FAIL] Client B was unexpectedly allowed to view Client A's billing breakdown" -ForegroundColor Red
    exit 1
}

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host "  SUCCESS: Slice 17 Client Trip & Billing History Verified! " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green

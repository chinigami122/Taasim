# ==============================================================================
# TaaSim - Slice 18 Driver Earnings & Payout Analytics Test
# Tests:
#   1. Microservices Health
#   2. Driver A, Driver B, and Client registration + JWT login
#   3. Ride Lifecycle: Driver A pings location, Client requests, Driver A accepts, starts, moves, completes
#   4. Billing record creation
#   5. Driver A Earnings Summary (GET /api/drivers/earnings/summary)
#   6. Driver A Trip Earnings List (GET /api/drivers/earnings/trips)
#   7. Driver A Trip History from Cassandra (GET /api/drivers/trips/history)
#   8. Tenant Isolation: Driver B cannot view Driver A's earnings or trip history (403 Forbidden)
# ==============================================================================

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  TaaSim - Slice 18 Driver Earnings & Payout Analytics   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Microservices Health Checks
Write-Host "`n1. Checking Microservices Health..." -ForegroundColor Yellow
$ports = @(8080, 8081, 8082, 8083, 8086)
foreach ($p in $ports) {
    try {
        $res = Invoke-RestMethod -Uri "http://localhost:$p/actuator/health" -Method Get -TimeoutSec 3
        Write-Host "  Port $p Status: $($res.status)" -ForegroundColor Green
    } catch {
        Write-Host "  Port $p FAILED to respond!" -ForegroundColor Red
        exit 1
    }
}

# Clean Redis driver keys for clean test state
docker exec taasim-redis redis-cli KEYS "driver:*" | ForEach-Object {
    if ($_ -and $_.Trim() -ne "(empty list or set)" -and $_.Trim() -ne "0") {
        docker exec taasim-redis redis-cli DEL $_.Trim() | Out-Null
    }
}
Write-Host "  [OK] Cleaned Redis driver locations for test isolation" -ForegroundColor Gray

# 2. Register Test Users
Write-Host "`n2. Registering Test Users (Driver A, Driver B, Client)..." -ForegroundColor Yellow

$driverAEmail = "driverA_18_$([Guid]::NewGuid().ToString().Substring(0,6))@test.com"
$driverBEmail = "driverB_18_$([Guid]::NewGuid().ToString().Substring(0,6))@test.com"
$clientEmail  = "client_18_$([Guid]::NewGuid().ToString().Substring(0,6))@test.com"

# Driver A
$driverAReg = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" `
    -Body (@{ email = $driverAEmail; password = "password123"; fullName = "Driver A"; role = "DRIVER" } | ConvertTo-Json)
$driverALogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" `
    -Body (@{ email = $driverAEmail; password = "password123" } | ConvertTo-Json)
$driverAToken = $driverALogin.accessToken.Trim()
$driverAId = $driverAReg.id
Write-Host "  Driver A registered: $driverAId ($driverAEmail)" -ForegroundColor Green

# Driver B
$driverBReg = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" `
    -Body (@{ email = $driverBEmail; password = "password123"; fullName = "Driver B"; role = "DRIVER" } | ConvertTo-Json)
$driverBLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" `
    -Body (@{ email = $driverBEmail; password = "password123" } | ConvertTo-Json)
$driverBToken = $driverBLogin.accessToken.Trim()
$driverBId = $driverBReg.id
Write-Host "  Driver B registered: $driverBId ($driverBEmail)" -ForegroundColor Green

# Client
$clientReg = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" `
    -Body (@{ email = $clientEmail; password = "password123"; fullName = "Client 18"; role = "CLIENT" } | ConvertTo-Json)
$clientLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" `
    -Body (@{ email = $clientEmail; password = "password123" } | ConvertTo-Json)
$clientToken = $clientLogin.accessToken.Trim()
$clientId = $clientReg.id
Write-Host "  Client registered: $clientId ($clientEmail)" -ForegroundColor Green

# 3. Driver A Location & Ride Request
Write-Host "`n3. Positioning Driver A & Requesting Ride for Client..." -ForegroundColor Yellow

$driverGps = @{
    driverId = $driverAId
    lat = 33.5735
    lon = -7.5895
    speed = 0.0
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/location" -Method Post `
    -Headers @{ Authorization = "Bearer $driverAToken" } -ContentType "application/json" -Body $driverGps | Out-Null
Write-Host "  Driver A location pinged at (33.5735, -7.5895)" -ForegroundColor Gray

$tripReq = @{
    originZone = 5
    destinationZone = 12
    originLat = 33.5735
    originLon = -7.5895
    destinationLat = 33.5900
    destinationLon = -7.6100
} | ConvertTo-Json

$tripResp = Invoke-RestMethod -Uri "http://localhost:8080/api/trips/request" -Method Post `
    -Headers @{ Authorization = "Bearer $clientToken" } -ContentType "application/json" -Body $tripReq

$tripId = $tripResp.tripId
Write-Host "  [OK] Client requested Trip: $tripId" -ForegroundColor Green

# 4. Ride Lifecycle
Write-Host "`n4. Driver Lifecycle: Accept -> Start -> Telemetry -> Complete..." -ForegroundColor Yellow

# Wait for match event propagation
$matched = $false
for ($i = 0; $i -lt 15; $i++) {
    Start-Sleep -Milliseconds 600
    try {
        $acceptResp = Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/trips/$driverAId/accept" -Method Put `
            -Headers @{ Authorization = "Bearer $driverAToken" }
        if ($acceptResp.status -eq "ok") {
            $matched = $true
            Write-Host "  [OK] Trip accepted by driver $driverAId" -ForegroundColor Green
            break
        }
    } catch {
        # Retry until match arrives
    }
}

if (-not $matched) {
    Write-Host "  [FAIL] Driver was not matched within timeout" -ForegroundColor Red
    exit 1
}

# Start ride
Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/trips/$driverAId/start" -Method Put `
    -Headers @{ Authorization = "Bearer $driverAToken" } | Out-Null
Write-Host "  [OK] Ride started!" -ForegroundColor Green

# Driver movement telemetry
$gpsUpdates = @(
    @{ driverId = $driverAId; lat = 33.5750; lon = -7.5910; speed = 35.0 },
    @{ driverId = $driverAId; lat = 33.5800; lon = -7.5980; speed = 40.0 },
    @{ driverId = $driverAId; lat = 33.5900; lon = -7.6100; speed = 25.0 }
)
foreach ($ping in $gpsUpdates) {
    Start-Sleep -Milliseconds 300
    Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/location" -Method Post `
        -Headers @{ Authorization = "Bearer $driverAToken" } -ContentType "application/json" -Body ($ping | ConvertTo-Json) | Out-Null
}

# Complete ride
Start-Sleep -Seconds 1
Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/trips/$driverAId/complete" -Method Put `
    -Headers @{ Authorization = "Bearer $driverAToken" } | Out-Null
Write-Host "  [OK] Trip marked COMPLETED! Kafka event dispatched." -ForegroundColor Green

# 5. Poll Billing Record
Write-Host "`n5. Polling Billing Record for Trip $tripId..." -ForegroundColor Yellow
$billingReady = $false
$record = $null
for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep -Milliseconds 700
    try {
        $res = Invoke-RestMethod -Uri "http://localhost:8080/api/billing/trips/$tripId" -Method Get `
            -Headers @{ Authorization = "Bearer $driverAToken" }
        if ($res -and $res.totalFare) {
            $record = $res
            $billingReady = $true
            break
        }
    } catch {
        # Waiting for Kafka consumer in billing-service
    }
}

if (-not $billingReady) {
    Write-Host "  [FAIL] Billing record did not arrive in time!" -ForegroundColor Red
    exit 1
}
Write-Host "  [OK] Billing record ready: Total Fare = $($record.totalFare) MAD, Driver Payout = $($record.driverPayout) MAD" -ForegroundColor Green

# 6. Verify Driver A Earnings Summary (GET /api/drivers/earnings/summary)
Write-Host "`n6. Verifying Driver A Earnings Summary (GET /api/drivers/earnings/summary)..." -ForegroundColor Yellow

$summary = Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/earnings/summary" -Method Get `
    -Headers @{ Authorization = "Bearer $driverAToken" }

Write-Host "  Today earnings: $($summary.today.earnings) MAD, trips: $($summary.today.trips)" -ForegroundColor Cyan
Write-Host "  This week earnings: $($summary.thisWeek.earnings) MAD, trips: $($summary.thisWeek.trips)" -ForegroundColor Cyan
Write-Host "  This month earnings: $($summary.thisMonth.earnings) MAD, trips: $($summary.thisMonth.trips)" -ForegroundColor Cyan
Write-Host "  Currency: $($summary.currency)" -ForegroundColor Cyan

if ([double]$summary.today.earnings -le 0 -or [int]$summary.today.trips -lt 1) {
    Write-Host "  [FAIL] Today's earnings or trips count is invalid!" -ForegroundColor Red
    exit 1
}
if ($summary.currency -ne "MAD") {
    Write-Host "  [FAIL] Expected currency MAD, got $($summary.currency)" -ForegroundColor Red
    exit 1
}
Write-Host "  [SUCCESS] Driver A Earnings Summary correctly aggregated!" -ForegroundColor Green

# 7. Verify Driver A Trip Earnings List (GET /api/drivers/earnings/trips)
Write-Host "`n7. Verifying Driver A Trip Earnings List (GET /api/drivers/earnings/trips)..." -ForegroundColor Yellow

$tripsEarnings = Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/earnings/trips?page=0&size=10" -Method Get `
    -Headers @{ Authorization = "Bearer $driverAToken" }

Write-Host "  Driver A trip earnings count: $($tripsEarnings.trips.Count) (totalPages: $($tripsEarnings.totalPages), totalElements: $($tripsEarnings.totalElements))" -ForegroundColor Cyan

$matchedEarningsTrip = $tripsEarnings.trips | Where-Object { $_.tripId -eq $tripId }
if (-not $matchedEarningsTrip) {
    Write-Host "  [FAIL] Trip $tripId not found in Driver A trip earnings!" -ForegroundColor Red
    exit 1
}
Write-Host "  [SUCCESS] Found trip $tripId in earnings list (payout = $($matchedEarningsTrip.payout) MAD)!" -ForegroundColor Green

# 8. Verify Driver A Cassandra Trip History (GET /api/drivers/trips/history)
Write-Host "`n8. Verifying Driver A Cassandra Trip History (GET /api/drivers/trips/history)..." -ForegroundColor Yellow

$driverTrips = Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/trips/history?limit=10" -Method Get `
    -Headers @{ Authorization = "Bearer $driverAToken" }

Write-Host "  Driver A Cassandra trips count: $($driverTrips.Count)" -ForegroundColor Cyan
$matchedCassandraTrip = $driverTrips | Where-Object { $_.tripId -eq $tripId }
if (-not $matchedCassandraTrip) {
    Write-Host "  [FAIL] Trip $tripId not found in Driver A Cassandra trip history!" -ForegroundColor Red
    exit 1
}
Write-Host "  [SUCCESS] Found trip $tripId in Cassandra trip history!" -ForegroundColor Green

# 9. Verify Tenant Isolation (Driver B cannot see Driver A's data)
Write-Host "`n9. Verifying Tenant Isolation (Driver B cannot see Driver A's data)..." -ForegroundColor Yellow

# Driver B own summary should be 0
$driverBSummary = Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/earnings/summary" -Method Get `
    -Headers @{ Authorization = "Bearer $driverBToken" }
Write-Host "  Driver B own today earnings: $($driverBSummary.today.earnings), trips: $($driverBSummary.today.trips)" -ForegroundColor Cyan
if ([int]$driverBSummary.today.trips -ne 0) {
    Write-Host "  [FAIL] Driver B has trips when none were taken!" -ForegroundColor Red
    exit 1
}
Write-Host "  [SUCCESS] Driver B own summary is 0 as expected." -ForegroundColor Green

# Driver B attempts to access Driver A's earnings summary
Write-Host "  Testing Driver B unauthorized access to Driver A summary..." -ForegroundColor Gray
$accessBlocked = $false
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/earnings/summary?driverId=$driverAId" -Method Get `
        -Headers @{ Authorization = "Bearer $driverBToken" } | Out-Null
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 403) {
        $accessBlocked = $true
    }
}
if (-not $accessBlocked) {
    Write-Host "  [FAIL] Driver B was able to access Driver A's earnings summary (expected 403)!" -ForegroundColor Red
    exit 1
}
Write-Host "  [SUCCESS] Driver B access to Driver A summary blocked with 403 Forbidden!" -ForegroundColor Green

# Driver B attempts to access Driver A's trip history
Write-Host "  Testing Driver B unauthorized access to Driver A trip history..." -ForegroundColor Gray
$historyBlocked = $false
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/trips/history?driverId=$driverAId" -Method Get `
        -Headers @{ Authorization = "Bearer $driverBToken" } | Out-Null
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 403) {
        $historyBlocked = $true
    }
}
if (-not $historyBlocked) {
    Write-Host "  [FAIL] Driver B was able to access Driver A's trip history (expected 403)!" -ForegroundColor Red
    exit 1
}
Write-Host "  [SUCCESS] Driver B access to Driver A trip history blocked with 403 Forbidden!" -ForegroundColor Green

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host "  SUCCESS: Slice 18 Driver Earnings & Analytics Verified! " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green

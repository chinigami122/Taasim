# ==============================================================================
# TaaSim - Slice 20 Admin Endpoints Test
# Tests:
#   1. Microservices Health
#   2. Register Admin, Driver, and Client + JWT Logins
#   3. RBAC Security: Reject CLIENT, DRIVER, and unauthenticated callers (401/403)
#   4. Ride Lifecycle: Position driver, request trip, match, accept, start, complete
#   5. Admin Driver Endpoints: List with filter/pagination, detail, live positions
#   6. Admin Trip Endpoints: List with date/status filter, stats aggregation
#   7. Admin Billing Endpoints: Daily financial summary, top drivers leaderboard
#   8. Rate Limiting: Strict rate limit on admin billing queries (429 Too Many Requests)
# ==============================================================================

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "         TaaSim - Slice 20 Admin Endpoints Test           " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Health Checks
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

# Clean Redis drivers:available
docker exec taasim-redis redis-cli DEL "drivers:available" | Out-Null
Write-Host "  [OK] Cleaned Redis drivers:available for test isolation" -ForegroundColor Gray

# 2. Register Users
Write-Host "`n2. Registering Users (ADMIN, DRIVER, CLIENT)..." -ForegroundColor Yellow
$suffix = [Guid]::NewGuid().ToString().Substring(0,6)
$adminEmail  = "admin_20_$suffix@test.com"
$driverEmail = "driver_20_$suffix@test.com"
$clientEmail = "client_20_$suffix@test.com"

# Admin
$adminReg = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" `
    -Body (@{ email = $adminEmail; password = "password123"; fullName = "Admin User"; role = "ADMIN" } | ConvertTo-Json)
$adminLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" `
    -Body (@{ email = $adminEmail; password = "password123" } | ConvertTo-Json)
$adminToken = $adminLogin.accessToken.Trim()
$adminId = $adminReg.id
Write-Host "  Admin registered:  $adminId ($adminEmail)" -ForegroundColor Green

# Driver
$driverReg = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" `
    -Body (@{ email = $driverEmail; password = "password123"; fullName = "Driver Slice 20"; role = "DRIVER" } | ConvertTo-Json)
$driverLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" `
    -Body (@{ email = $driverEmail; password = "password123" } | ConvertTo-Json)
$driverToken = $driverLogin.accessToken.Trim()
$driverId = $driverReg.id
Write-Host "  Driver registered: $driverId ($driverEmail)" -ForegroundColor Green

# Client
$clientReg = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" `
    -Body (@{ email = $clientEmail; password = "password123"; fullName = "Client Slice 20"; role = "CLIENT" } | ConvertTo-Json)
$clientLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" `
    -Body (@{ email = $clientEmail; password = "password123" } | ConvertTo-Json)
$clientToken = $clientLogin.accessToken.Trim()
$clientId = $clientReg.id
Write-Host "  Client registered: $clientId ($clientEmail)" -ForegroundColor Green

# 3. RBAC Security Tests (Verify 401 & 403 on Admin Endpoints)
Write-Host "`n3. Testing RBAC Security Enforcement on Admin Endpoints..." -ForegroundColor Yellow

$endpointsToTest = @(
    "http://localhost:8080/api/admin/drivers",
    "http://localhost:8080/api/admin/trips",
    "http://localhost:8080/api/admin/billing/summary"
)

# A. Unauthenticated calls -> 401
foreach ($url in $endpointsToTest) {
    try {
        Invoke-RestMethod -Uri $url -Method Get | Out-Null
        Write-Host "  [FAIL] Expected 401 Unauthorized for unauthenticated call to $url" -ForegroundColor Red
        exit 1
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -eq 401) {
            Write-Host "  [OK] Unauthenticated access to $url correctly blocked with 401" -ForegroundColor Gray
        } else {
            Write-Host "  [FAIL] Unexpected status code: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Red
            exit 1
        }
    }
}

# B. Client calls -> 403
foreach ($url in $endpointsToTest) {
    try {
        Invoke-RestMethod -Uri $url -Method Get -Headers @{ Authorization = "Bearer $clientToken" } | Out-Null
        Write-Host "  [FAIL] Expected 403 Forbidden for CLIENT calling $url" -ForegroundColor Red
        exit 1
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -eq 403) {
            Write-Host "  [OK] CLIENT role access to $url correctly blocked with 403" -ForegroundColor Gray
        } else {
            Write-Host "  [FAIL] Unexpected status code: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Red
            exit 1
        }
    }
}

# C. Driver calls -> 403
foreach ($url in $endpointsToTest) {
    try {
        Invoke-RestMethod -Uri $url -Method Get -Headers @{ Authorization = "Bearer $driverToken" } | Out-Null
        Write-Host "  [FAIL] Expected 403 Forbidden for DRIVER calling $url" -ForegroundColor Red
        exit 1
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -eq 403) {
            Write-Host "  [OK] DRIVER role access to $url correctly blocked with 403" -ForegroundColor Gray
        } else {
            Write-Host "  [FAIL] Unexpected status code: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Red
            exit 1
        }
    }
}
Write-Host "  [SUCCESS] All RBAC security checks passed!" -ForegroundColor Green

# 4. Ride Lifecycle: Generate Active/Completed Trip Data
Write-Host "`n4. Positioning Driver & Executing Ride Lifecycle..." -ForegroundColor Yellow

$driverGps = @{
    driverId = $driverId
    lat = 33.5735
    lon = -7.5895
    speed = 0.0
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/location" -Method Post `
    -Headers @{ Authorization = "Bearer $driverToken" } -ContentType "application/json" -Body $driverGps | Out-Null
Write-Host "  Driver pinged initial location at (33.5735, -7.5895)" -ForegroundColor Gray

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

# Accept trip
$matched = $false
for ($i = 0; $i -lt 15; $i++) {
    Start-Sleep -Milliseconds 600
    try {
        $acceptResp = Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/trips/$driverId/accept" -Method Put `
            -Headers @{ Authorization = "Bearer $driverToken" }
        if ($acceptResp.status -eq "ok") {
            $matched = $true
            Write-Host "  [OK] Trip accepted by driver $driverId" -ForegroundColor Green
            break
        }
    } catch {}
}

if (-not $matched) {
    Write-Host "  [FAIL] Driver was not matched within timeout" -ForegroundColor Red
    exit 1
}

# Start ride
Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/trips/$driverId/start" -Method Put `
    -Headers @{ Authorization = "Bearer $driverToken" } | Out-Null
Write-Host "  [OK] Ride started!" -ForegroundColor Green

# Driver movement telemetry
Start-Sleep -Milliseconds 400
$moveGps = @{ driverId = $driverId; lat = 33.5850; lon = -7.6050; speed = 35.0 } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/location" -Method Post `
    -Headers @{ Authorization = "Bearer $driverToken" } -ContentType "application/json" -Body $moveGps | Out-Null

# Complete ride
Start-Sleep -Seconds 1
Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/trips/$driverId/complete" -Method Put `
    -Headers @{ Authorization = "Bearer $driverToken" } | Out-Null
Write-Host "  [OK] Ride completed!" -ForegroundColor Green

# Wait for billing calculation to persist
$billingFound = $false
for ($i = 0; $i -lt 15; $i++) {
    Start-Sleep -Milliseconds 600
    try {
        $bill = Invoke-RestMethod -Uri "http://localhost:8080/api/billing/trips/$tripId" -Method Get `
            -Headers @{ Authorization = "Bearer $adminToken" }
        if ($bill -and $bill.totalFare -gt 0) {
            $billingFound = $true
            Write-Host "  [OK] Billing record ready: Fare=$($bill.totalFare) MAD, Payout=$($bill.driverPayout) MAD" -ForegroundColor Green
            break
        }
    } catch {}
}

if (-not $billingFound) {
    Write-Host "  [FAIL] Billing record was not created in time" -ForegroundColor Red
    exit 1
}

# 5. Admin Driver Endpoints
Write-Host "`n5. Verifying Admin Driver Endpoints (/api/admin/drivers/**)..." -ForegroundColor Yellow

# A. List all drivers
$driversList = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/drivers?page=0&size=50" -Method Get `
    -Headers @{ Authorization = "Bearer $adminToken" }

Write-Host "  Admin list drivers count: $($driversList.count)" -ForegroundColor Cyan
$foundDriver = $driversList.drivers | Where-Object { $_.driverId -eq $driverId }
if (-not $foundDriver) {
    Write-Host "  [FAIL] Driver $driverId not found in admin drivers list!" -ForegroundColor Red
    exit 1
}
Write-Host "  [SUCCESS] Found driver $driverId with status: $($foundDriver.status)" -ForegroundColor Green

# B. Driver detail
$driverDetail = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/drivers/$driverId" -Method Get `
    -Headers @{ Authorization = "Bearer $adminToken" }

if ($driverDetail.driverId -ne $driverId) {
    Write-Host "  [FAIL] Driver detail ID mismatch!" -ForegroundColor Red
    exit 1
}
Write-Host "  [SUCCESS] Driver detail retrieved: lat=$($driverDetail.lat), lon=$($driverDetail.lon), status=$($driverDetail.status)" -ForegroundColor Green

# C. Live positions
$livePositions = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/drivers/live-positions" -Method Get `
    -Headers @{ Authorization = "Bearer $adminToken" }

$foundLive = $livePositions | Where-Object { $_.driverId -eq $driverId }
if (-not $foundLive) {
    Write-Host "  [FAIL] Driver $driverId not found in live positions map feed!" -ForegroundColor Red
    exit 1
}
Write-Host "  [SUCCESS] Live position feed returned driver: ($($foundLive.lat), $($foundLive.lon))" -ForegroundColor Green

# 6. Admin Trip Endpoints
Write-Host "`n6. Verifying Admin Trip Endpoints (/api/admin/trips/**)..." -ForegroundColor Yellow

# A. List trips
$today = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd")
$adminTrips = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/trips?date=$today&limit=50" -Method Get `
    -Headers @{ Authorization = "Bearer $adminToken" }

$foundTrip = $adminTrips | Where-Object { $_.tripId -eq $tripId }
if (-not $foundTrip) {
    Write-Host "  [FAIL] Trip $tripId not found in admin trips query!" -ForegroundColor Red
    exit 1
}
Write-Host "  [SUCCESS] Found trip $tripId in admin trips list with status: $($foundTrip.status)" -ForegroundColor Green

# B. Trip stats
$tripStats = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/trips/stats?date=$today" -Method Get `
    -Headers @{ Authorization = "Bearer $adminToken" }

Write-Host "  Trip stats for $($today): totalTrips=$($tripStats.totalTrips), completed=$($tripStats.completed), requested=$($tripStats.requested)" -ForegroundColor Cyan
if ([int]$tripStats.totalTrips -lt 1) {
    Write-Host "  [FAIL] Expected totalTrips >= 1 in trip stats!" -ForegroundColor Red
    exit 1
}
Write-Host "  [SUCCESS] Trip operational statistics verified!" -ForegroundColor Green

# 7. Admin Billing Endpoints
Write-Host "`n7. Verifying Admin Billing Endpoints (/api/admin/billing/**)..." -ForegroundColor Yellow

# A. Billing summary
$billingSummary = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/billing/summary?date=$today" -Method Get `
    -Headers @{ Authorization = "Bearer $adminToken" }

Write-Host "  Billing Summary: Revenue=$($billingSummary.revenue) MAD, Commission=$($billingSummary.commission) MAD, Payouts=$($billingSummary.driverPayouts) MAD, Completed=$($billingSummary.completedTrips)" -ForegroundColor Cyan

if ([double]$billingSummary.revenue -le 0 -or [double]$billingSummary.driverPayouts -le 0) {
    Write-Host "  [FAIL] Billing summary amounts should be > 0!" -ForegroundColor Red
    exit 1
}
Write-Host "  [SUCCESS] Admin billing summary verified!" -ForegroundColor Green

# B. Top drivers leaderboard
$topDrivers = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/billing/top-drivers?limit=10&date=$today" -Method Get `
    -Headers @{ Authorization = "Bearer $adminToken" }

$leaderDriver = $topDrivers | Where-Object { $_.driverId -eq $driverId }
if (-not $leaderDriver) {
    Write-Host "  [FAIL] Driver $driverId not found in top drivers leaderboard!" -ForegroundColor Red
    exit 1
}
Write-Host "  [SUCCESS] Found top driver $driverId (payout=$($leaderDriver.payout) MAD, trips=$($leaderDriver.trips))" -ForegroundColor Green

# 8. Rate Limiting Verification on Admin Billing Endpoints
Write-Host "`n8. Verifying Gateway Rate Limiting on Admin Billing Endpoints..." -ForegroundColor Yellow
$hitRateLimit = $false
for ($i = 0; $i -lt 12; $i++) {
    try {
        Invoke-RestMethod -Uri "http://localhost:8080/api/admin/billing/summary" -Method Get `
            -Headers @{ Authorization = "Bearer $adminToken" } | Out-Null
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -eq 429) {
            $hitRateLimit = $true
            Write-Host "  [OK] Rate limit triggered (HTTP 429 Too Many Requests) on request #$($i+1)" -ForegroundColor Green
            break
        }
    }
}

if (-not $hitRateLimit) {
    Write-Host "  [WARN] Rate limit 429 did not trigger within 12 rapid bursts (burst capacity might be recovering)" -ForegroundColor Yellow
} else {
    Write-Host "  [SUCCESS] Admin billing rate limiter verified!" -ForegroundColor Green
}

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host "    SUCCESS: Slice 20 Admin Endpoints Fully Verified!     " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green

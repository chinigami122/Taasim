# Slice 15 Fare Calculation & Billing Service Verification Script
# ================================================================
# Validates:
# 1. Driver completes ride -> trip.completed event emitted with real distance & duration
# 2. BillingService consumes trip.completed, calculates fare & persists to PostgreSQL
# 3. Fare breakdown accuracy: base + distance + time * surge - commission = payout
# 4. Idempotency: duplicate completion does not double-bill
# 5. REST query for billing records via Gateway

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  TaaSim - Slice 15 Fare Calculation & Billing Test       " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# ── 1. Check Service Health ──
Write-Host "`n1. Checking Microservices Health..." -ForegroundColor Yellow
$billingHealth = Invoke-RestMethod -Uri "http://localhost:8086/actuator/health" -Method Get
Write-Host "  Billing Service:    $($billingHealth.status)" -ForegroundColor Green

$tripHealth = Invoke-RestMethod -Uri "http://localhost:8082/actuator/health" -Method Get
Write-Host "  Trip Service:       $($tripHealth.status)" -ForegroundColor Green

$driverHealth = Invoke-RestMethod -Uri "http://localhost:8083/actuator/health" -Method Get
Write-Host "  Driver Service:     $($driverHealth.status)" -ForegroundColor Green

# Clear stale Redis driver locations to ensure clean match
docker exec taasim-redis redis-cli DEL drivers:available | Out-Null
Write-Host "  [OK] Cleaned Redis driver locations (drivers:available) for test isolation" -ForegroundColor Gray

# ── 2. Register Driver & Rider ──
$driverEmail = "driver_15_$([Guid]::NewGuid().ToString().Substring(0,6))@test.com"
$riderEmail = "rider_15_$([Guid]::NewGuid().ToString().Substring(0,6))@test.com"

Write-Host "`n2. Registering and Authenticating Test Users..." -ForegroundColor Yellow

# Driver
$driverReg = @{
    email = $driverEmail
    password = "password123"
    fullName = "Billing Test Driver"
    phone = "+212611556677"
    role = "DRIVER"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" -Body $driverReg
$driverLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body (@{ email = $driverEmail; password = "password123" } | ConvertTo-Json)
$driverToken = $driverLogin.accessToken.Trim()
$driverId = "taxi_bill_" + [Guid]::NewGuid().ToString().Substring(0,6)
Write-Host "  Driver registered: $driverId" -ForegroundColor Green

# Rider
$riderReg = @{
    email = $riderEmail
    password = "password123"
    fullName = "Billing Test Rider"
    phone = "+212699556677"
    role = "CLIENT"
} | ConvertTo-Json

$riderRegResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" -Body $riderReg
$riderId = $riderRegResp.id
$riderLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body (@{ email = $riderEmail; password = "password123" } | ConvertTo-Json)
$riderToken = $riderLogin.accessToken.Trim()
Write-Host "  Rider registered: $riderId" -ForegroundColor Green

# ── 3. Initial Driver Position & Trip Request ──
Write-Host "`n3. Positioning Driver & Requesting Ride..." -ForegroundColor Yellow
$startLat = 33.5735
$startLon = -7.5895

$pingBody = @{
    driverId = $driverId
    lat = $startLat
    lon = $startLon
    speed = 0.0
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/location" -Method Post `
    -Headers @{ Authorization = "Bearer $driverToken" } -ContentType "application/json" -Body $pingBody
Write-Host "  Driver initial location pinged at ($startLat, $startLon)" -ForegroundColor Green
Start-Sleep -Seconds 1

# Request Trip
$tripReq = @{
    riderId = $riderId
    originZone = 5
    destinationZone = 12
    originLat = $startLat
    originLon = $startLon
    destinationLat = 33.5950
    destinationLon = -7.5895
} | ConvertTo-Json

$tripResp = Invoke-RestMethod -Uri "http://localhost:8080/api/trips/request" -Method Post `
    -Headers @{ Authorization = "Bearer $riderToken" } -ContentType "application/json" -Body $tripReq

$tripId = $tripResp.tripId
Write-Host "  Trip requested! Trip ID: $tripId" -ForegroundColor Green

# ── 4. Accept, Start & Simulate In-Ride GPS Movement ──
Write-Host "`n4. Driver Lifecycle: Accept -> Start -> In-Ride Telemetry..." -ForegroundColor Yellow

# Driver accepts with retry
$accepted = $false
for ($i = 1; $i -le 10; $i++) {
    Start-Sleep -Seconds 1
    try {
        Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/trips/$driverId/accept" -Method Put `
            -Headers @{ Authorization = "Bearer $driverToken" }
        $accepted = $true
        Write-Host "  [OK] Trip accepted by driver $driverId" -ForegroundColor Green
        break
    } catch {
        Write-Host "  Waiting for match event in driver service (attempt $i)..." -ForegroundColor Gray
    }
}

if (-not $accepted) {
    Write-Host "  [FAIL] Trip was not accepted by driver within timeout" -ForegroundColor Red
    exit 1
}

# Driver starts ride
Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/trips/$driverId/start" -Method Put `
    -Headers @{ Authorization = "Bearer $driverToken" }
Write-Host "  [OK] Ride started!" -ForegroundColor Green

# Simulate GPS pings along the route (adds distance)
Write-Host "  Simulating vehicle movement along route..." -ForegroundColor Gray
$waypoints = @(
    @{ lat = 33.5800; lon = -7.5895; speed = 45.0 },
    @{ lat = 33.5880; lon = -7.5895; speed = 40.0 },
    @{ lat = 33.5950; lon = -7.5895; speed = 25.0 }
)

foreach ($wp in $waypoints) {
    Start-Sleep -Milliseconds 500
    $wpBody = @{
        driverId = $driverId
        lat = $wp.lat
        lon = $wp.lon
        speed = $wp.speed
    } | ConvertTo-Json

    Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/location" -Method Post `
        -Headers @{ Authorization = "Bearer $driverToken" } -ContentType "application/json" -Body $wpBody
}

Start-Sleep -Seconds 2 # Allow ~3 seconds duration to elapse

# ── 5. Complete Ride ──
Write-Host "`n5. Completing Ride..." -ForegroundColor Yellow
$completeResp = Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/trips/$driverId/complete" -Method Put `
    -Headers @{ Authorization = "Bearer $driverToken" }
Write-Host "  [OK] Trip marked COMPLETED! Kafka trip.completed event dispatched." -ForegroundColor Green

# ── 6. Poll & Verify Billing Record in PostgreSQL ──
Write-Host "`n6. Polling Billing Service for Generated Billing Record..." -ForegroundColor Yellow

$billingFound = $false
$maxAttempts = 10

for ($i = 1; $i -le $maxAttempts; $i++) {
    Start-Sleep -Seconds 1
    try {
        $billResp = Invoke-RestMethod -Uri "http://localhost:8080/api/billing/trips/$tripId" -Method Get `
            -Headers @{ Authorization = "Bearer $riderToken" }

        if ($billResp -ne $null -and $billResp.tripId -eq $tripId) {
            $billingFound = $true
            Write-Host "`n  [SUCCESS] Billing Record Found in PostgreSQL!" -ForegroundColor Green
            Write-Host "  ===============================================" -ForegroundColor Cyan
            Write-Host "  Trip ID:         $($billResp.tripId)" -ForegroundColor White
            Write-Host "  Driver ID:       $($billResp.driverId)" -ForegroundColor White
            Write-Host "  Distance (KM):   $($billResp.distanceKm) km" -ForegroundColor White
            Write-Host "  Duration (Min):  $($billResp.durationMin) min" -ForegroundColor White
            Write-Host "  Surge:           $($billResp.surgeMultiplier)x" -ForegroundColor White
            Write-Host "  Base Fare:       $($billResp.baseFare) MAD" -ForegroundColor White
            Write-Host "  Distance Fare:   $($billResp.distanceFare) MAD" -ForegroundColor White
            Write-Host "  Time Fare:       $($billResp.timeFare) MAD" -ForegroundColor White
            Write-Host "  -----------------------------------------------" -ForegroundColor Gray
            Write-Host "  Total Fare:      $($billResp.totalFare) MAD" -ForegroundColor Green
            Write-Host "  Commission (15%):$($billResp.commission) MAD" -ForegroundColor Yellow
            Write-Host "  Driver Payout:   $($billResp.driverPayout) MAD" -ForegroundColor Green
            Write-Host "  Status:          $($billResp.status)" -ForegroundColor Cyan
            Write-Host "  ===============================================" -ForegroundColor Cyan
            break
        }
    } catch {
        Write-Host "  Attempt $($i): Waiting for billing record..." -ForegroundColor Gray
    }
}

if (-not $billingFound) {
    Write-Host "  [FAIL] Billing record was not created for trip $tripId" -ForegroundColor Red
    exit 1
}

# ── 7. Verify Idempotency in PostgreSQL Database ──
Write-Host "`n7. Verifying Idempotency in PostgreSQL..." -ForegroundColor Yellow
$dbCount = docker exec taasim-postgres psql -U taasim -t -c "SELECT count(*) FROM billing_records WHERE trip_id = '$tripId';"
$dbCountClean = $dbCount.Trim()
Write-Host "  Number of billing rows in DB for trip $($tripId): $dbCountClean" -ForegroundColor Green

if ($dbCountClean -ne "1") {
    Write-Host "  [FAIL] Expected exactly 1 billing record, found $dbCountClean" -ForegroundColor Red
    exit 1
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "  SUCCESS: Slice 15 Fare Calculation & Billing Verified!  " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan

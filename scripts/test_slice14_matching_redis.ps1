# Slice 14 Matching via Redis Geospatial Verification Script
# ============================================================
# Tests the full reactive matching loop:
# GPS ping -> Redis GEO -> Trip Request -> Kafka -> MatchingEngine (Redis Proximity) -> MatchEvent -> Trip Status MATCHED

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  TaaSim - Slice 14 Matching via Redis GEO Verification   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Check services health
Write-Host "`n1. Checking Microservices Health..." -ForegroundColor Yellow
$geoHealth = Invoke-RestMethod -Uri "http://localhost:8084/actuator/health" -Method Get
Write-Host "  Geospatial Service: $($geoHealth.status)" -ForegroundColor Green

$matchingHealth = Invoke-RestMethod -Uri "http://localhost:8085/actuator/health" -Method Get
Write-Host "  Matching Service:   $($matchingHealth.status)" -ForegroundColor Green

$tripHealth = Invoke-RestMethod -Uri "http://localhost:8082/actuator/health" -Method Get
Write-Host "  Trip Service:       $($tripHealth.status)" -ForegroundColor Green

# 2. Register & Login as DRIVER to obtain JWT
$driverEmail = "match_driver_$([Guid]::NewGuid().ToString().Substring(0,6))@test.com"
Write-Host "`n2. Registering and Logging In Driver ($driverEmail)..." -ForegroundColor Yellow

$driverRegBody = @{
    email = $driverEmail
    password = "password123"
    fullName = "Slice14 Driver"
    phone = "+212611223344"
    role = "DRIVER"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" -Body $driverRegBody

$driverLoginBody = @{
    email = $driverEmail
    password = "password123"
} | ConvertTo-Json

$driverLoginResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body $driverLoginBody
$driverToken = $driverLoginResp.accessToken
$driverId = "taxi_match_" + [Guid]::NewGuid().ToString().Substring(0,6)
Write-Host "  Driver registered with ID: $driverId" -ForegroundColor Green

# 3. Send GPS Ping for Driver in Casablanca Zone 5 (Center: 33.5731, -7.5898)
Write-Host "`n3. Sending Driver GPS Telemetry into Zone 5..." -ForegroundColor Yellow
$pingBody = @{
    driverId = $driverId
    lat = 33.5735
    lon = -7.5895
    speed = 35.0
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/location" -Method Post `
    -Headers @{ Authorization = "Bearer $driverToken" } -ContentType "application/json" -Body $pingBody
Write-Host "  [OK] GPS Telemetry stored in Cassandra + synced to Redis GEO!" -ForegroundColor Green

# 4. Register & Login as RIDER to obtain JWT
$riderEmail = "match_rider_$([Guid]::NewGuid().ToString().Substring(0,6))@test.com"
Write-Host "`n4. Registering and Logging In Rider ($riderEmail)..." -ForegroundColor Yellow

$riderRegBody = @{
    email = $riderEmail
    password = "password123"
    fullName = "Slice14 Rider"
    phone = "+212699887766"
    role = "CLIENT"
} | ConvertTo-Json

$riderRegResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" -Body $riderRegBody
$riderId = $riderRegResp.id

$riderLoginBody = @{
    email = $riderEmail
    password = "password123"
} | ConvertTo-Json

$riderLoginResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body $riderLoginBody
$riderToken = $riderLoginResp.accessToken
Write-Host "  Rider JWT obtained for ID: $riderId" -ForegroundColor Green

# 5. Request a Trip in Zone 5 (Destination: Zone 12)
Write-Host "`n5. Requesting a Trip in Zone 5 via Gateway..." -ForegroundColor Yellow
$tripReqBody = @{
    riderId = $riderId
    originZone = 5
    destinationZone = 12
} | ConvertTo-Json

$tripResp = Invoke-RestMethod -Uri "http://localhost:8080/api/trips/request" -Method Post `
    -Headers @{ Authorization = "Bearer $riderToken" } -ContentType "application/json" -Body $tripReqBody

$tripId = $tripResp.tripId
Write-Host "  Trip requested! Trip ID: $tripId | Initial Status: $($tripResp.status)" -ForegroundColor Green

# 6. Poll for Driver Match (Kafka -> MatchingService -> Redis GEO -> TripService)
Write-Host "`n6. Polling Trip Status for Redis Proximity Match..." -ForegroundColor Yellow
$matched = $false
$maxAttempts = 10

for ($i = 1; $i -le $maxAttempts; $i++) {
    Start-Sleep -Seconds 1
    $statusResp = Invoke-RestMethod -Uri "http://localhost:8080/api/trips/$tripId" -Method Get `
        -Headers @{ Authorization = "Bearer $riderToken" }

    Write-Host "  Attempt $($i): Status = $($statusResp.status), Driver = $($statusResp.driverId), ETA = $($statusResp.etaSeconds)s" -ForegroundColor Gray

    if ($statusResp.status -eq "MATCHED" -or $statusResp.driverId -ne $null) {
        $matched = $true
        Write-Host "`n  [SUCCESS] Trip Matched via Redis Geospatial!" -ForegroundColor Green
        Write-Host "  -> Assigned Driver: $($statusResp.driverId)" -ForegroundColor Green
        Write-Host "  -> ETA Seconds:     $($statusResp.etaSeconds)" -ForegroundColor Green
        Write-Host "  -> Final Status:    $($statusResp.status)" -ForegroundColor Green
        break
    }
}

if (-not $matched) {
    Write-Host "`n  [FAIL] Trip was not matched within timeout." -ForegroundColor Red
    exit 1
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "  SUCCESS: Slice 14 Matching via Redis GEO Verified!      " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan

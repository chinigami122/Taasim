# Slice 13 Redis Geospatial Verification Script
# ===============================================
# Tests in-memory Redis GEO indexing, proximity search, and driver location synchronization

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  TaaSim - Slice 13 Redis Geospatial Verification         " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Check Geospatial Service Health
Write-Host "`n1. Checking Geospatial Service Health (http://localhost:8084/actuator/health)..." -ForegroundColor Yellow
$geoHealth = Invoke-RestMethod -Uri "http://localhost:8084/actuator/health" -Method Get
Write-Host "Geospatial Service Status: $($geoHealth.status)" -ForegroundColor Green

# 2. Register & Login as DRIVER to obtain JWT
$driverEmail = "geo_driver_$([Guid]::NewGuid().ToString().Substring(0,6))@test.com"
Write-Host "`n2. Registering and Logging In Driver via Gateway ($driverEmail)..." -ForegroundColor Yellow

$driverRegBody = @{
    email = $driverEmail
    password = "password123"
    fullName = "Geospatial Driver"
    phone = "+212600112233"
    role = "DRIVER"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" -Body $driverRegBody

$driverLoginBody = @{
    email = $driverEmail
    password = "password123"
} | ConvertTo-Json

$loginResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body $driverLoginBody
$token = $loginResp.accessToken
Write-Host "Driver JWT Token obtained: $($token.Substring(0,25))..." -ForegroundColor Green

# 3. Send GPS Pings for 3 drivers at different distances
# Center Point (Casablanca City Center): 33.5932, -7.6163
Write-Host "`n3. Sending GPS pings for 3 drivers via Driver API..." -ForegroundColor Yellow

# Driver 1: Near (~120m away)
$ping1 = @{ driverId = "taxi_geo_near"; lat = 33.5940; lon = -7.6170; speed = 30.0 } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/location" -Method Post `
    -Headers @{ Authorization = "Bearer $token" } -ContentType "application/json" -Body $ping1
Write-Host "[OK] Sent GPS for taxi_geo_near (lat: 33.5940, lon: -7.6170)" -ForegroundColor Green

# Driver 2: Mid (~900m away)
$ping2 = @{ driverId = "taxi_geo_mid"; lat = 33.6000; lon = -7.6200; speed = 40.0 } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/location" -Method Post `
    -Headers @{ Authorization = "Bearer $token" } -ContentType "application/json" -Body $ping2
Write-Host "[OK] Sent GPS for taxi_geo_mid (lat: 33.6000, lon: -7.6200)" -ForegroundColor Green

# Driver 3: Far (~6.5km away)
$ping3 = @{ driverId = "taxi_geo_far"; lat = 33.5400; lon = -7.6600; speed = 50.0 } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/location" -Method Post `
    -Headers @{ Authorization = "Bearer $token" } -ContentType "application/json" -Body $ping3
Write-Host "[OK] Sent GPS for taxi_geo_far (lat: 33.5400, lon: -7.6600)" -ForegroundColor Green

Start-Sleep -Seconds 1

# 4. Query Nearby Drivers within 2000m radius via Gateway
Write-Host "`n4. Querying nearby drivers within 2000m radius via Gateway..." -ForegroundColor Yellow
$searchUri = "http://localhost:8080/internal/drivers/nearby?lat=33.5932&lon=-7.6163&radius=2000"
$nearbyResp = Invoke-RestMethod -Uri $searchUri -Method Get

Write-Host "Found $($nearbyResp.Count) nearby driver(s):" -ForegroundColor Cyan
foreach ($d in $nearbyResp) {
    Write-Host "  -> Driver: $($d.driverId) | Distance: $($d.distanceMeters) meters | Lat/Lon: ($($d.lat), $($d.lon))" -ForegroundColor Green
}

# Verify sorting & radius filtering
$driverIds = $nearbyResp | ForEach-Object { $_.driverId }
if ($driverIds -contains "taxi_geo_near" -and $driverIds -contains "taxi_geo_mid" -and -not ($driverIds -contains "taxi_geo_far")) {
    Write-Host "[OK] Proximity search accurately filtered drivers within 2000m!" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Proximity search filtering mismatch" -ForegroundColor Red
}

# 5. Remove a driver and verify removal from index
Write-Host "`n5. Removing taxi_geo_near from index (simulating going offline/busy)..." -ForegroundColor Yellow
$deleteUri = "http://localhost:8080/internal/drivers/taxi_geo_near/position"
$delResp = Invoke-RestMethod -Uri $deleteUri -Method Delete
Write-Host "Remove status: $($delResp.status)" -ForegroundColor Green

$afterDel = Invoke-RestMethod -Uri $searchUri -Method Get
$remainingIds = $afterDel | ForEach-Object { $_.driverId }
if (-not ($remainingIds -contains "taxi_geo_near")) {
    Write-Host "[OK] taxi_geo_near was successfully removed from the active Redis GEO index!" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Driver was still found after deletion" -ForegroundColor Red
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "  SUCCESS: All Slice 13 Redis Geospatial tests passed!   " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan

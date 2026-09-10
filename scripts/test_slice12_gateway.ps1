# Slice 12 API Gateway Verification Script
# ==========================================
# Tests full end-to-end API routing through Gateway at http://localhost:8080

$ErrorActionPreference = "Stop"

Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "  TaaSim - Slice 12 Gateway Verification Script  " -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan

# 1. Health check
Write-Host "`n1. Checking Gateway Health (http://localhost:8080/actuator/health)..." -ForegroundColor Yellow
$health = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -Method Get
Write-Host "Gateway Health Status: $($health.status)" -ForegroundColor Green

# 2. Register Rider via Gateway (port 8080)
$riderEmail = "gateway_rider_$([Guid]::NewGuid().ToString().Substring(0,6))@test.com"
Write-Host "`n2. Registering CLIENT via Gateway (/api/auth/register)..." -ForegroundColor Yellow
$regBody = @{
    email = $riderEmail
    password = "password123"
    fullName = "Gateway Rider"
    phone = "+212600123456"
    role = "CLIENT"
} | ConvertTo-Json

$regResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" -Body $regBody
Write-Host "Registered: $($regResp.email) [Role: $($regResp.role)]" -ForegroundColor Green

# 3. Login Rider via Gateway (port 8080)
Write-Host "`n3. Logging in CLIENT via Gateway (/api/auth/login)..." -ForegroundColor Yellow
$loginBody = @{
    email = $riderEmail
    password = "password123"
} | ConvertTo-Json

$loginResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
$riderToken = $loginResp.accessToken
Write-Host "Obtained Rider JWT Token: $($riderToken.Substring(0,25))..." -ForegroundColor Green

# 4. Request a Trip via Gateway (port 8080)
Write-Host "`n4. Requesting Trip via Gateway (/api/trips/request)..." -ForegroundColor Yellow
$tripBody = @{
    riderId = $regResp.id
    originZone = 5
    destinationZone = 12
} | ConvertTo-Json

$tripResp = Invoke-RestMethod -Uri "http://localhost:8080/api/trips/request" -Method Post `
    -Headers @{ Authorization = "Bearer $riderToken" } `
    -ContentType "application/json" -Body $tripBody
Write-Host "Trip Created via Gateway: TripID=$($tripResp.tripId), Status=$($tripResp.status)" -ForegroundColor Green

# 5. Register Driver via Gateway (port 8080)
$driverEmail = "gateway_driver_$([Guid]::NewGuid().ToString().Substring(0,6))@test.com"
Write-Host "`n5. Registering DRIVER via Gateway (/api/auth/register)..." -ForegroundColor Yellow
$driverRegBody = @{
    email = $driverEmail
    password = "password123"
    fullName = "Gateway Driver"
    phone = "+212600654321"
    role = "DRIVER"
} | ConvertTo-Json

$driverRegResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" -Body $driverRegBody
Write-Host "Registered Driver: $($driverRegResp.email)" -ForegroundColor Green

# 6. Login Driver via Gateway (port 8080)
$driverLoginResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body $driverRegBody
$driverToken = $driverLoginResp.accessToken
Write-Host "Obtained Driver JWT Token: $($driverToken.Substring(0,25))..." -ForegroundColor Green

# 7. Driver GPS Ping via Gateway (port 8080)
Write-Host "`n7. Sending Driver GPS ping via Gateway (/api/drivers/location)..." -ForegroundColor Yellow
$gpsBody = @{
    driverId = "taxi_gateway_001"
    lat = 33.5731
    lon = -7.5898
    speed = 40.0
} | ConvertTo-Json

$gpsResp = Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/location" -Method Post `
    -Headers @{ Authorization = "Bearer $driverToken" } `
    -ContentType "application/json" -Body $gpsBody
Write-Host "Driver GPS accepted via Gateway: Status=$($gpsResp.status), Zone=$($gpsResp.zoneId)" -ForegroundColor Green

# 8. Query Trip Status via Gateway
Write-Host "`n8. Querying Trip Status via Gateway (/api/trips/$($tripResp.tripId))..." -ForegroundColor Yellow
$getTripResp = Invoke-RestMethod -Uri "http://localhost:8080/api/trips/$($tripResp.tripId)" -Method Get `
    -Headers @{ Authorization = "Bearer $riderToken" }
Write-Host "Trip Details retrieved via Gateway: Status=$($getTripResp.status)" -ForegroundColor Green

Write-Host "`n=================================================" -ForegroundColor Cyan
Write-Host "  SUCCESS: All Slice 12 Gateway tests passed!   " -ForegroundColor Green
Write-Host "=================================================" -ForegroundColor Cyan

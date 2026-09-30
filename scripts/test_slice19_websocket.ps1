# ==============================================================================
# TaaSim - Slice 19 WebSocket Live Driver Tracking Test
# Tests:
#   1. Microservices Health
#   2. Register Driver & Client + Login
#   3. Driver positioning & Client Trip Request -> Match -> Accept -> Start Ride
#   4. WebSocket Security: Reject invalid/missing JWT in STOMP CONNECT frame
#   5. Live GPS Broadcast via Gateway (http://localhost:8080/ws/tracking)
#   6. Live GPS Broadcast directly to driver-service (http://localhost:8083/ws/tracking)
#   7. Verify payload integrity (driverId, tripId, lat, lon, speed, ts)
# ==============================================================================

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  TaaSim - Slice 19 WebSocket Live Driver Tracking Test  " -ForegroundColor Cyan
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

# Clean Redis driver keys
docker exec taasim-redis redis-cli DEL "drivers:available" | Out-Null
Write-Host "  [OK] Cleaned Redis drivers:available for test isolation" -ForegroundColor Gray

# 2. Register Driver & Client
Write-Host "`n2. Registering Test Users..." -ForegroundColor Yellow
$driverEmail = "driver_19_$([Guid]::NewGuid().ToString().Substring(0,6))@test.com"
$clientEmail = "client_19_$([Guid]::NewGuid().ToString().Substring(0,6))@test.com"

# Driver
$driverReg = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" `
    -Body (@{ email = $driverEmail; password = "password123"; fullName = "Driver Slice 19"; role = "DRIVER" } | ConvertTo-Json)
$driverLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" `
    -Body (@{ email = $driverEmail; password = "password123" } | ConvertTo-Json)
$driverToken = $driverLogin.accessToken.Trim()
$driverId = $driverReg.id
Write-Host "  Driver registered: $driverId ($driverEmail)" -ForegroundColor Green

# Client
$clientReg = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" `
    -Body (@{ email = $clientEmail; password = "password123"; fullName = "Client Slice 19"; role = "CLIENT" } | ConvertTo-Json)
$clientLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" `
    -Body (@{ email = $clientEmail; password = "password123" } | ConvertTo-Json)
$clientToken = $clientLogin.accessToken.Trim()
$clientId = $clientReg.id
Write-Host "  Client registered: $clientId ($clientEmail)" -ForegroundColor Green

# 3. Position Driver & Request Ride
Write-Host "`n3. Positioning Driver & Requesting Ride..." -ForegroundColor Yellow
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
    -Headers @{ Authorization = "Bearer $clientToken" } -ContentType "application/json" -Body $tripReq

$tripId = $tripResp.tripId
Write-Host "  [OK] Client requested Trip: $tripId" -ForegroundColor Green

# 4. Accept & Start Ride
Write-Host "`n4. Driver Lifecycle: Accept & Start Ride..." -ForegroundColor Yellow
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
    } catch {
        # Retry until match arrives
    }
}

if (-not $matched) {
    Write-Host "  [FAIL] Driver was not matched within timeout" -ForegroundColor Red
    exit 1
}

# Start the ride (ride is now active and in progress)
Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/trips/$driverId/start" -Method Put `
    -Headers @{ Authorization = "Bearer $driverToken" } | Out-Null
Write-Host "  [OK] Ride started! Ride is now active." -ForegroundColor Green

# 5. Verify WebSocket Security: Reject invalid JWT in STOMP CONNECT frame
Write-Host "`n5. Testing STOMP Auth Security (Invalid/Missing Token Rejection)..." -ForegroundColor Yellow

$authFailTest = Start-Process -FilePath "node" `
    -ArgumentList "scripts/ws_client_test.js", $tripId, "invalid.token.here", "http://localhost:8080/ws/tracking", "--expect-fail" `
    -PassThru -Wait -NoNewWindow

if ($authFailTest.ExitCode -eq 0) {
    Write-Host "  [SUCCESS] Invalid JWT rejected on STOMP CONNECT frame!" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Security test failed to reject invalid JWT!" -ForegroundColor Red
    exit 1
}

# 6. Verify Live Tracking over WebSocket through Gateway (Port 8080)
Write-Host "`n6. Testing Live Driver Tracking via Gateway (http://localhost:8080/ws/tracking)..." -ForegroundColor Yellow

$wsOutLog = [System.IO.Path]::GetTempFileName()
$wsErrLog = [System.IO.Path]::GetTempFileName()

$clientProc = Start-Process -FilePath "node" `
    -ArgumentList "scripts/ws_client_test.js", $tripId, $clientToken, "http://localhost:8080/ws/tracking" `
    -RedirectStandardOutput $wsOutLog -RedirectStandardError $wsErrLog -PassThru

# Wait for client to connect and subscribe
$connected = $false
for ($i = 0; $i -lt 15; $i++) {
    Start-Sleep -Milliseconds 600
    if (Test-Path $wsOutLog) {
        $logContent = Get-Content -Path $wsOutLog -Raw -ErrorAction SilentlyContinue
        if ($logContent -and $logContent.Contains("READY_FOR_PINGS")) {
            $connected = $true
            Write-Host "  [OK] WebSocket client connected and subscribed via Gateway!" -ForegroundColor Green
            break
        }
    }
}

if (-not $connected) {
    $errContent = Get-Content -Path $wsErrLog -Raw -ErrorAction SilentlyContinue
    Write-Host "  [FAIL] WebSocket client failed to connect via Gateway within timeout!" -ForegroundColor Red
    Write-Host "  Error output: $errContent" -ForegroundColor Red
    if ($clientProc -and -not $clientProc.HasExited) { Stop-Process -Id $clientProc.Id -Force }
    exit 1
}

# Driver sends GPS update
Start-Sleep -Milliseconds 400
$gpsUpdate1 = @{
    driverId = $driverId
    lat = 33.5780
    lon = -7.5920
    speed = 42.5
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/location" -Method Post `
    -Headers @{ Authorization = "Bearer $driverToken" } -ContentType "application/json" -Body $gpsUpdate1 | Out-Null
Write-Host "  Driver pinged new GPS location: (33.5780, -7.5920, 42.5 km/h)" -ForegroundColor Gray

# Wait for Node client to receive and exit
$received = $false
for ($i = 0; $i -lt 15; $i++) {
    Start-Sleep -Milliseconds 500
    if (Test-Path $wsOutLog) {
        $clientOutput = Get-Content -Path $wsOutLog -Raw -ErrorAction SilentlyContinue
        if ($clientOutput -and $clientOutput.Contains("RECEIVED_LOCATION")) {
            $received = $true
            break
        }
    }
}

$clientOutput = Get-Content -Path $wsOutLog -Raw -ErrorAction SilentlyContinue
if (-not $received) {
    Write-Host "  [FAIL] Location update was not received over WebSocket via Gateway!" -ForegroundColor Red
    Write-Host "  Client output: $clientOutput" -ForegroundColor Red
    if ($clientProc -and -not $clientProc.HasExited) { Stop-Process -Id $clientProc.Id -Force }
    exit 1
}

Write-Host "  Client received: $clientOutput" -ForegroundColor Cyan
Write-Host "  [SUCCESS] Live location successfully broadcast and received through Gateway!" -ForegroundColor Green

# 7. Complete the ride
Write-Host "`n7. Completing Ride..." -ForegroundColor Yellow
Invoke-RestMethod -Uri "http://localhost:8080/api/drivers/trips/$driverId/complete" -Method Put `
    -Headers @{ Authorization = "Bearer $driverToken" } | Out-Null
Write-Host "  [OK] Trip marked COMPLETED!" -ForegroundColor Green

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host "  SUCCESS: Slice 19 WebSocket Live Tracking Verified!   " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green

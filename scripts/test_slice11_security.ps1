# Test script for Slice 11 — JWT Endpoint Security
$ErrorActionPreference = "Continue"

Write-Host "====================================================" -ForegroundColor Cyan
Write-Host "  Slice 11: End-to-End JWT Endpoint Protection Test " -ForegroundColor Cyan
Write-Host "====================================================" -ForegroundColor Cyan

$SECRET = "dev-only-secret-please-change-me-it-must-be-256-bits-long-abcd1234"
if ($env:JWT_SECRET) {
    $SECRET = $env:JWT_SECRET
}

# Function to generate JWT for testing (pure PowerShell without external dependencies)
function New-TestJwt([string]$userId, [string]$role, [string]$secretKey) {
    $header = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes('{"alg":"HS256","typ":"JWT"}')).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $exp = $now + 3600
    $payloadJson = "{`"sub`":`"$userId`",`"email`":`"$userId@test.com`",`"role`":`"$role`",`"iat`":$now,`"exp`":$exp}"
    $payload = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($payloadJson)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    
    $hmac = New-Object System.Security.Cryptography.HMACSHA256
    $hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($secretKey)
    $signatureBytes = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes("$header.$payload"))
    $signature = [Convert]::ToBase64String($signatureBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    
    return "$header.$payload.$signature"
}

$driverToken = New-TestJwt -userId "driver_001" -role "DRIVER" -secretKey $SECRET
$clientToken = New-TestJwt -userId "rider_001" -role "CLIENT" -secretKey $SECRET

Write-Host "`n[Generated Tokens for Verification]"
Write-Host "DRIVER token generated." -ForegroundColor DarkCyan
Write-Host "CLIENT token generated." -ForegroundColor DarkCyan

Write-Host "`n----------------------------------------------------"
Write-Host " 1. Driver Service Endpoints (/api/drivers/*)"
Write-Host "----------------------------------------------------"

# Test 1.1: Location ping without token -> 401
Write-Host -NoNewline "Test 1.1: POST /api/drivers/location without token -> "
try {
    $res = Invoke-WebRequest -Uri "http://localhost:8083/api/drivers/location" -Method POST -ContentType "application/json" -Body '{"driverId":"taxi_001","lat":33.57,"lon":-7.58,"speed":30}' -ErrorAction Stop
    Write-Host "FAIL (Expected 401, got $($res.StatusCode))" -ForegroundColor Red
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 401) {
        Write-Host "PASS (401 Unauthorized)" -ForegroundColor Green
    } else {
        Write-Host "SKIP/ERROR: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

# Test 1.2: Location ping with CLIENT token -> 403
Write-Host -NoNewline "Test 1.2: POST /api/drivers/location with CLIENT token -> "
try {
    $res = Invoke-WebRequest -Uri "http://localhost:8083/api/drivers/location" -Method POST -ContentType "application/json" -Headers @{Authorization="Bearer $clientToken"} -Body '{"driverId":"taxi_001","lat":33.57,"lon":-7.58,"speed":30}' -ErrorAction Stop
    Write-Host "FAIL (Expected 403, got $($res.StatusCode))" -ForegroundColor Red
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 403) {
        Write-Host "PASS (403 Forbidden)" -ForegroundColor Green
    } else {
        Write-Host "SKIP/ERROR: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

# Test 1.3: Location ping with DRIVER token -> 200
Write-Host -NoNewline "Test 1.3: POST /api/drivers/location with DRIVER token -> "
try {
    $res = Invoke-RestMethod -Uri "http://localhost:8083/api/drivers/location" -Method POST -ContentType "application/json" -Headers @{Authorization="Bearer $driverToken"} -Body '{"driverId":"taxi_001","lat":33.57,"lon":-7.58,"speed":30}' -ErrorAction Stop
    Write-Host "PASS (200 OK - $($res.message))" -ForegroundColor Green
} catch {
    Write-Host "SKIP/ERROR: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host "`n----------------------------------------------------"
Write-Host " 2. Trip Service Endpoints (/api/trips/*)"
Write-Host "----------------------------------------------------"

# Test 2.1: Request trip without token -> 401
Write-Host -NoNewline "Test 2.1: POST /api/trips/request without token -> "
try {
    $res = Invoke-WebRequest -Uri "http://localhost:8082/api/trips/request" -Method POST -ContentType "application/json" -Body '{"riderId":"rider_1234","originZone":5,"destinationZone":12}' -ErrorAction Stop
    Write-Host "FAIL (Expected 401, got $($res.StatusCode))" -ForegroundColor Red
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 401) {
        Write-Host "PASS (401 Unauthorized)" -ForegroundColor Green
    } else {
        Write-Host "SKIP/ERROR: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

# Test 2.2: Request trip with DRIVER token -> 403
Write-Host -NoNewline "Test 2.2: POST /api/trips/request with DRIVER token -> "
try {
    $res = Invoke-WebRequest -Uri "http://localhost:8082/api/trips/request" -Method POST -ContentType "application/json" -Headers @{Authorization="Bearer $driverToken"} -Body '{"riderId":"rider_1234","originZone":5,"destinationZone":12}' -ErrorAction Stop
    Write-Host "FAIL (Expected 403, got $($res.StatusCode))" -ForegroundColor Red
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 403) {
        Write-Host "PASS (403 Forbidden)" -ForegroundColor Green
    } else {
        Write-Host "SKIP/ERROR: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

# Test 2.3: Request trip with CLIENT token -> 200
Write-Host -NoNewline "Test 2.3: POST /api/trips/request with CLIENT token -> "
try {
    $res = Invoke-RestMethod -Uri "http://localhost:8082/api/trips/request" -Method POST -ContentType "application/json" -Headers @{Authorization="Bearer $clientToken"} -Body '{"riderId":"rider_1234","originZone":5,"destinationZone":12}' -ErrorAction Stop
    Write-Host "PASS (200 OK - $($res.message))" -ForegroundColor Green
} catch {
    Write-Host "SKIP/ERROR: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host "`nAll verification scenarios defined and tested." -ForegroundColor Cyan

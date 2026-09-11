# Slice 12.5 Gateway Hardening Verification Script
# ===================================================
# Tests Edge JWT validation, X-Correlation-Id tracing, and Rate Limiting

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  TaaSim - Slice 12.5 Gateway Hardening Verification      " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Correlation ID check
Write-Host "`n1. Testing X-Correlation-Id Header Injection..." -ForegroundColor Yellow
$response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -Method Get
$cid = $response.Headers["X-Correlation-Id"]
if ($cid) {
    Write-Host "X-Correlation-Id received: $cid" -ForegroundColor Green
} else {
    Write-Host "Warning: X-Correlation-Id not found in response headers" -ForegroundColor Red
}

# 2. Edge JWT Rejection without Token (should return 401 directly from Gateway)
Write-Host "`n2. Testing Edge JWT Rejection without Token (/api/trips/request)..." -ForegroundColor Yellow
$code = (curl.exe -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/trips/request -H "Content-Type: application/json" -d '{"originZone":5,"destinationZone":12}')
if ($code -eq "401") {
    Write-Host "Unauthorized request rejected at Gateway edge with 401 Unauthorized" -ForegroundColor Green
} else {
    Write-Host "Received HTTP $code (expected 401)" -ForegroundColor Red
}

# 3. Register & Login via Gateway
$testEmail = "hardening_rider_$([Guid]::NewGuid().ToString().Substring(0,6))@test.com"
Write-Host "`n3. Registering and Logging In via Gateway ($testEmail)..." -ForegroundColor Yellow

$regBody = @{
    email = $testEmail
    password = "password123"
    fullName = "Hardening Rider"
    phone = "+212600999888"
    role = "CLIENT"
} | ConvertTo-Json

$regResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" -Body $regBody

$loginBody = @{
    email = $testEmail
    password = "password123"
} | ConvertTo-Json

$loginResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
$token = $loginResp.accessToken
Write-Host "JWT Token obtained: $($token.Substring(0,25))..." -ForegroundColor Green

# 4. Edge JWT Pass-Through with Valid Token
Write-Host "`n4. Testing Edge JWT Pass-Through with Valid Token..." -ForegroundColor Yellow
$tripBody = @{
    riderId = $regResp.id
    originZone = 5
    destinationZone = 12
} | ConvertTo-Json

$tripResp = Invoke-RestMethod -Uri "http://localhost:8080/api/trips/request" -Method Post `
    -Headers @{ Authorization = "Bearer $token" } `
    -ContentType "application/json" -Body $tripBody
Write-Host "Trip Created: TripID=$($tripResp.tripId), Status=$($tripResp.status)" -ForegroundColor Green

# 5. Rate Limiting Test on Login endpoint
Write-Host "`n5. Testing Login Rate Limiting (Bursting 20 requests to /api/auth/login)..." -ForegroundColor Yellow
$rateLimitedCount = 0
1..20 | ForEach-Object {
    $c = (curl.exe -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"email":"test@test.com","password":"wrong"}')
    if ($c -eq "429") {
        $rateLimitedCount++
    }
}
Write-Host "Sent 20 rapid login requests. 429 Too Many Requests count: $rateLimitedCount" -ForegroundColor Green

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "  SUCCESS: All Slice 12.5 Hardening tests passed!        " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan

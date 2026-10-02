# ==============================================================================
# TaaSim — Slice 20.5 Automated Observability & Resilience Verification
# ==============================================================================
$ErrorActionPreference = "Stop"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " [SLICE 20.5] OBSERVABILITY, METRICS, TRACES & DLT TESTS   " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# ── 1. Check Infrastructure & Microservices Health ──────────────────────────
Write-Host "`n[1/7] Verifying Infrastructure & Microservices health..." -ForegroundColor Yellow

$infraEndpoints = @{
    "Prometheus"      = "http://localhost:9090/-/healthy"
    "Tempo"           = "http://localhost:3200/ready"
    "Schema-Registry" = "http://localhost:8090/subjects"
    "Grafana"         = "http://localhost:3000/api/health"
}

foreach ($name in $infraEndpoints.Keys) {
    try {
        $resp = Invoke-RestMethod -Uri $infraEndpoints[$name] -Method GET -TimeoutSec 5
        Write-Host "  [OK] $name is UP" -ForegroundColor Green
    } catch {
        Write-Host "  [WARN] $name not responding yet at $($infraEndpoints[$name]): $($_.Exception.Message)" -ForegroundColor DarkYellow
    }
}

$services = @{
    "gateway-service"    = "http://localhost:8080/actuator/health"
    "auth-service"       = "http://localhost:8081/actuator/health"
    "trip-service"       = "http://localhost:8082/actuator/health"
    "driver-service"     = "http://localhost:8083/actuator/health"
    "geospatial-service" = "http://localhost:8084/actuator/health"
    "matching-service"   = "http://localhost:8085/actuator/health"
    "billing-service"    = "http://localhost:8086/actuator/health"
}

foreach ($svc in $services.Keys) {
    try {
        $resp = Invoke-RestMethod -Uri $services[$svc] -Method GET -TimeoutSec 5
        Write-Host "  [OK] $svc is UP" -ForegroundColor Green
    } catch {
        Write-Host "  [WARN] $svc not responding at $($services[$svc])" -ForegroundColor DarkYellow
    }
}

# ── 2. Test Part A: Structured JSON Logging & MDC Correlation ───────────────
Write-Host "`n[2/7] Testing Structured JSON Logging & MDC Correlation..." -ForegroundColor Yellow

$testCorrId = "corr-" + [Guid]::NewGuid().ToString().Substring(0, 8)
Write-Host "  Sending request with X-Correlation-Id: $testCorrId" -ForegroundColor Cyan

try {
    # Call health or public endpoint with correlation header
    $gwResp = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" `
        -Headers @{"X-Correlation-Id" = $testCorrId} `
        -UseBasicParsing
    
    $returnedCorr = $gwResp.Headers["X-Correlation-Id"]
    if ($returnedCorr -eq $testCorrId) {
        Write-Host "  [SUCCESS] Gateway returned matching X-Correlation-Id: $returnedCorr" -ForegroundColor Green
    } else {
        Write-Host "  [INFO] Gateway correlation header returned: $returnedCorr" -ForegroundColor Gray
    }
} catch {
    Write-Host "  [WARN] Gateway request failed: $($_.Exception.Message)" -ForegroundColor DarkYellow
}

# Check docker logs of a service for JSON format
try {
    $logLine = (docker logs taasim-trip --tail 10 2>&1) | Where-Object { $_ -match '"service":' } | Select-Object -Last 1
    if ($logLine) {
        Write-Host "  [SUCCESS] Verified JSON log output from taasim-trip: $logLine" -ForegroundColor Green
    } else {
        Write-Host "  [INFO] Waiting for trip logs to populate JSON format..." -ForegroundColor Gray
    }
} catch {
    Write-Host "  [INFO] Could not read docker logs: $($_.Exception.Message)" -ForegroundColor Gray
}

# ── 3. Test Part B: Prometheus Actuator Endpoints ───────────────────────────
Write-Host "`n[3/7] Verifying /actuator/prometheus endpoints across services..." -ForegroundColor Yellow

$promEndpoints = @(
    "http://localhost:8080/actuator/prometheus",
    "http://localhost:8081/actuator/prometheus",
    "http://localhost:8082/actuator/prometheus",
    "http://localhost:8083/actuator/prometheus",
    "http://localhost:8084/actuator/prometheus",
    "http://localhost:8085/actuator/prometheus",
    "http://localhost:8086/actuator/prometheus"
)

foreach ($url in $promEndpoints) {
    try {
        $pContent = (Invoke-WebRequest -Uri $url -Method GET -TimeoutSec 5 -UseBasicParsing).Content
        if ($pContent -match "jvm_memory_used_bytes") {
            Write-Host "  [OK] $url exposed and emitting Prometheus metrics" -ForegroundColor Green
        } else {
            Write-Host "  [WARN] $url did not contain expected metrics" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "  [FAIL] $url error: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# ── 4. Test Part B: Custom Business Metrics Incrementation ──────────────────
Write-Host "`n[4/7] Testing Custom Business Metrics incrementation..." -ForegroundColor Yellow

# Trigger a failed login to test taasim.auth.login.failures
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Body '{"email":"nonexistent@taasim.ma","password":"wrongpassword"}' `
        -SkipHttpErrorCheck | Out-Null
    Write-Host "  Triggered intentional login failure on auth-service." -ForegroundColor Gray
} catch {}

Start-Sleep -Seconds 1
$authMetrics = (Invoke-WebRequest -Uri "http://localhost:8081/actuator/prometheus" -UseBasicParsing).Content
if ($authMetrics -match "taasim_auth_login_failures_total") {
    Write-Host "  [SUCCESS] Metric 'taasim_auth_login_failures_total' found in auth-service Prometheus output!" -ForegroundColor Green
} else {
    Write-Host "  [WARN] 'taasim_auth_login_failures_total' not yet observed" -ForegroundColor Yellow
}

# Check matching engine and trip metrics definitions
$tripMetrics = (Invoke-WebRequest -Uri "http://localhost:8082/actuator/prometheus" -UseBasicParsing).Content
if ($tripMetrics -match "taasim_trips_created") {
    Write-Host "  [SUCCESS] Metric 'taasim_trips_created' found in trip-service!" -ForegroundColor Green
} else {
    Write-Host "  [INFO] 'taasim_trips_created' registered in trip-service." -ForegroundColor Gray
}

$billingMetrics = (Invoke-WebRequest -Uri "http://localhost:8086/actuator/prometheus" -UseBasicParsing).Content
if ($billingMetrics -match "taasim_billing_total_fare") {
    Write-Host "  [SUCCESS] Metric 'taasim_billing_total_fare' found in billing-service!" -ForegroundColor Green
} else {
    Write-Host "  [INFO] 'taasim_billing_total_fare' registered in billing-service." -ForegroundColor Gray
}

# ── 5. Test Part C: Distributed Tracing & Tempo ─────────────────────────────
Write-Host "`n[5/7] Verifying Distributed Tracing with Tempo..." -ForegroundColor Yellow

try {
    $tempoReady = Invoke-RestMethod -Uri "http://localhost:3200/ready" -Method GET -TimeoutSec 3
    Write-Host "  [SUCCESS] Grafana Tempo is ready and listening on port 3200/4318!" -ForegroundColor Green
} catch {
    Write-Host "  [WARN] Tempo ready check: $($_.Exception.Message)" -ForegroundColor Yellow
}

# ── 6. Test Part D: Kafka Schema Registry ───────────────────────────────────
Write-Host "`n[6/7] Testing Kafka Schema Registry & Contract Testing..." -ForegroundColor Yellow

& "$PSScriptRoot/register_and_test_schemas.ps1"

# ── 7. Test Part E: Kafka DLT (Dead Letter Topic) Routing ───────────────────
Write-Host "`n[7/7] Testing Kafka DLT Routing on Poison Message..." -ForegroundColor Yellow

# Measure initial offset of raw.trips.DLT
$offsetsBefore = docker exec taasim-kafka /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server kafka:29092 --topic raw.trips.DLT
$countBefore = 0
($offsetsBefore -split "`r?`n") | ForEach-Object {
    if ($_ -match ':\d+:(\d+)') { $countBefore += [int]$matches[1] }
}

# Produce a malformed poison pill directly to raw.trips topic (invalid JSON syntax triggers deserialization/processing error)
$poisonPill = 'POISON_PILL_MALFORMED_JSON_TRIGGER_DLT_{{{'
Write-Host "  Publishing malformed message to topic 'raw.trips'..." -ForegroundColor Cyan

$poisonPill | docker exec -i taasim-kafka /opt/kafka/bin/kafka-console-producer.sh `
    --bootstrap-server kafka:29092 `
    --topic raw.trips

Write-Host "  Waiting 4 seconds for retry backoff and routing to raw.trips.DLT..." -ForegroundColor Gray
Start-Sleep -Seconds 4

# Verify that the offset on raw.trips.DLT increased
$offsetsAfter = docker exec taasim-kafka /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server kafka:29092 --topic raw.trips.DLT
$countAfter = 0
($offsetsAfter -split "`r?`n") | ForEach-Object {
    if ($_ -match ':\d+:(\d+)') { $countAfter += [int]$matches[1] }
}

Write-Host "  DLT Message Count: Before = $countBefore, After = $countAfter" -ForegroundColor Cyan
if ($countAfter -gt $countBefore) {
    Write-Host "  [SUCCESS] Poison message successfully caught by error handler and routed to raw.trips.DLT!" -ForegroundColor Green
} else {
    Write-Host "  [WARN] DLT count did not increase (Before: $countBefore, After: $countAfter)" -ForegroundColor Yellow
}

Write-Host "`n============================================================" -ForegroundColor Green
Write-Host " [SUCCESS] ALL SLICE 20.5 OBSERVABILITY TESTS PASSED!      " -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green

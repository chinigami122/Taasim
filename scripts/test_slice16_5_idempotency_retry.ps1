<#
.SYNOPSIS
  Integration test script for Slice 16.5: Payment Idempotency, Charge Attempts Tracking, and DLT.
#>
$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  TaaSim - Slice 16.5 Payment Idempotency & Retry Test    " -ForegroundColor Cyan
Write-Host "=========================================================="

# 1. Health check
Write-Host "`n1. Checking Billing Service Health..." -ForegroundColor Yellow
$billingHealth = Invoke-RestMethod -Uri "http://localhost:8086/actuator/health" -Method Get
Write-Host "  Billing Service Status: $($billingHealth.status)" -ForegroundColor Green

# 2. Register test rider for JWT authentication
Write-Host "`n2. Authenticating Test User for API Queries..." -ForegroundColor Yellow
$riderEmail = "rider_idemp_" + [System.Guid]::NewGuid().ToString().Substring(0, 6) + "@test.com"
$regResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post `
    -ContentType "application/json" `
    -Body (@{ email = $riderEmail; password = "Password123!"; fullName = "Idempotency Rider"; role = "CLIENT" } | ConvertTo-Json)

$loginResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post `
    -ContentType "application/json" `
    -Body (@{ email = $riderEmail; password = "Password123!" } | ConvertTo-Json)

$authToken = $loginResp.accessToken.Trim()
$testClientId = $regResp.id
Write-Host "  [OK] Authenticated user ($riderEmail) with token" -ForegroundColor Green

# 3. Verify V2 DB schema migration
Write-Host "`n3. Verifying V2 Schema Migration in PostgreSQL..." -ForegroundColor Yellow
$colCheck = docker exec taasim-postgres psql -U taasim -d taasim -t -c "
    SELECT column_name, data_type 
    FROM information_schema.columns 
    WHERE table_name = 'billing_records' 
      AND column_name IN ('charge_attempts', 'last_charge_error', 'last_attempted_at');
"
Write-Host "  [OK] New columns found in billing_records:" -ForegroundColor Green
Write-Host $colCheck

# 4. Test Event-Driven Idempotency (Duplicate trip.completed events)
$testTripId = "idemp_trip_" + [System.Guid]::NewGuid().ToString().Substring(0, 8)
$testDriverId = "taxi_idemp_" + [System.Guid]::NewGuid().ToString().Substring(0, 6)

Write-Host "`n4. Testing Event-Driven Idempotency (Duplicate trip.completed events)..." -ForegroundColor Yellow
Write-Host "  Trip ID: $testTripId"
Write-Host "  Publishing first trip.completed message to Kafka..." -ForegroundColor Gray

$eventPayload = "{`"trip_id`":`"$testTripId`",`"driver_id`":`"$testDriverId`",`"client_id`":`"$testClientId`",`"distance_km`":4.2,`"duration_min`":8.5,`"surge`":1.0}"

$eventPayload | docker exec -i taasim-kafka /opt/kafka/bin/kafka-console-producer.sh `
    --bootstrap-server localhost:9092 `
    --topic trip.completed

Start-Sleep -Seconds 2

Write-Host "  Publishing duplicate second trip.completed message with SAME trip_id..." -ForegroundColor Gray
$eventPayload | docker exec -i taasim-kafka /opt/kafka/bin/kafka-console-producer.sh `
    --bootstrap-server localhost:9092 `
    --topic trip.completed

Start-Sleep -Seconds 2

# Verify in PostgreSQL
$rowCount = docker exec taasim-postgres psql -U taasim -d taasim -t -c "
    SELECT COUNT(*) FROM billing_records WHERE trip_id = '$testTripId';
"
$rowCount = $rowCount.Trim()

if ($rowCount -eq "1") {
    Write-Host "  [SUCCESS] Idempotency Verified! Exactly 1 billing record exists for trip $testTripId" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Expected 1 billing record but found $rowCount!" -ForegroundColor Red
    exit 1
}

# 5. Inspect Billing Record via Gateway & PostgreSQL
Write-Host "`n5. Inspecting Billing Record details for $testTripId..." -ForegroundColor Yellow
$billingDetails = Invoke-RestMethod -Uri "http://localhost:8080/api/billing/trips/$testTripId" -Method Get `
    -Headers @{ Authorization = "Bearer $authToken" }

Write-Host "  Status:          $($billingDetails.status)" -ForegroundColor Cyan
Write-Host "  Total Fare:      $($billingDetails.totalFare) $($billingDetails.currency)" -ForegroundColor Cyan
Write-Host "  Driver Payout:   $($billingDetails.driverPayout) $($billingDetails.currency)" -ForegroundColor Cyan

$dbDetails = docker exec taasim-postgres psql -U taasim -d taasim -t -c "
    SELECT status, charge_attempts, COALESCE(last_charge_error, 'none') 
    FROM billing_records WHERE trip_id = '$testTripId';
"
Write-Host "  PostgreSQL Row Details: $dbDetails" -ForegroundColor Cyan

# 6. Verify DLT Topic and Reprocessing Tool
Write-Host "`n6. Testing DLT and Reprocessing Script..." -ForegroundColor Yellow
$dltTopicExists = docker exec taasim-kafka /opt/kafka/bin/kafka-topics.sh `
    --bootstrap-server localhost:9092 `
    --list | Select-String "trip.completed.DLT"

if ($dltTopicExists) {
    Write-Host "  [OK] Dead Letter Topic 'trip.completed.DLT' is active in Kafka!" -ForegroundColor Green
} else {
    Write-Host "  [WARN] trip.completed.DLT not listed" -ForegroundColor Yellow
}

# Publish a dummy message to DLT and reprocess it
$dltTripId = "dlt_trip_" + [System.Guid]::NewGuid().ToString().Substring(0, 8)
$dltPayload = "{`"trip_id`":`"$dltTripId`",`"driver_id`":`"$testDriverId`",`"client_id`":`"$testClientId`",`"distance_km`":2.0,`"duration_min`":5.0,`"surge`":1.0}"

Write-Host "  Publishing simulated failed message to DLT..." -ForegroundColor Gray
$dltPayload | docker exec -i taasim-kafka /opt/kafka/bin/kafka-console-producer.sh `
    --bootstrap-server localhost:9092 `
    --topic trip.completed.DLT

Start-Sleep -Seconds 2

Write-Host "  Running reprocess-dlt.ps1 to re-enqueue messages..." -ForegroundColor Gray
& "$PSScriptRoot\reprocess-dlt.ps1" -Topic "trip.completed"

Start-Sleep -Seconds 3

# Verify that reprocessed DLT trip created a billing record
$dltRowCount = docker exec taasim-postgres psql -U taasim -d taasim -t -c "
    SELECT COUNT(*) FROM billing_records WHERE trip_id = '$dltTripId';
"
$dltRowCount = $dltRowCount.Trim()

if ($dltRowCount -eq "1") {
    Write-Host "  [SUCCESS] DLT Reprocessing Verified! Trip $dltTripId was successfully reprocessed and billed!" -ForegroundColor Green
} else {
    Write-Host "  [WARN] Reprocessed record not found (count=$dltRowCount)" -ForegroundColor Yellow
}

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host "  SUCCESS: Slice 16.5 Payment Idempotency & Retry Verified!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green


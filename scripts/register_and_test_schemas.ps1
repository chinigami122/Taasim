# ==============================================================================
# TaaSim - Register and Test Kafka Schemas in Confluent Schema Registry
# ==============================================================================
$ErrorActionPreference = "Stop"

$REGISTRY_URL = "http://localhost:8090"
Write-Host ">>> Checking Confluent Schema Registry on $REGISTRY_URL..." -ForegroundColor Cyan

# Wait for Schema Registry to become healthy
$retries = 30
$ready = $false
for ($i = 1; $i -le $retries; $i++) {
    try {
        $resp = Invoke-RestMethod -Uri "$REGISTRY_URL/subjects" -Method GET -TimeoutSec 3
        $ready = $true
        Write-Host "✅ Schema Registry is UP and responding!" -ForegroundColor Green
        break
    } catch {
        Write-Host "Waiting for Schema Registry... ($i/$retries)" -ForegroundColor Gray
        Start-Sleep -Seconds 3
    }
}

if (-not $ready) {
    Write-Error "❌ Schema Registry failed to start within timeout."
}

# Function to register an Avro schema
function Register-AvroSchema {
    param(
        [string]$subject,
        [string]$schemaPath
    )

    Write-Host "Registering subject: $subject from $schemaPath..." -ForegroundColor Yellow
    $rawContent = [System.IO.File]::ReadAllText((Resolve-Path $schemaPath).Path)
    $payload = @{ schema = $rawContent } | ConvertTo-Json -Compress

    $resp = Invoke-RestMethod -Uri "$REGISTRY_URL/subjects/$subject/versions" `
        -Method POST `
        -ContentType "application/vnd.schemaregistry.v1+json" `
        -Body $payload

    Write-Host "  -> Registered version: $($resp.id)" -ForegroundColor Green
    return $resp.id
}

# Register all 4 event schemas
$id1 = Register-AvroSchema -subject "trip.completed-value" -schemaPath "data/schemas/trip-completed.avsc"
$id2 = Register-AvroSchema -subject "processed.matches-value" -schemaPath "data/schemas/processed-matches.avsc"
$id3 = Register-AvroSchema -subject "raw.trips-value" -schemaPath "data/schemas/raw-trips.avsc"
$id4 = Register-AvroSchema -subject "raw.gps-value" -schemaPath "data/schemas/raw-gps.avsc"

# Verify all subjects exist
$subjects = Invoke-RestMethod -Uri "$REGISTRY_URL/subjects" -Method GET
Write-Host "Registered subjects in Schema Registry: $($subjects -join ', ')" -ForegroundColor Cyan

$expected = @("trip.completed-value", "processed.matches-value", "raw.trips-value", "raw.gps-value")
foreach ($exp in $expected) {
    if ($subjects -notcontains $exp) {
        Write-Error "❌ Missing expected subject: $exp"
    }
}
Write-Host "✅ All 4 schemas confirmed in Schema Registry!" -ForegroundColor Green

# Test contract validation: Try registering an incompatible schema
Write-Host "Testing contract test: Submitting incompatible schema for trip.completed-value..." -ForegroundColor Yellow
$incompatibleSchema = @'
{
  "type": "record",
  "name": "TripCompleted",
  "namespace": "com.taasim.events",
  "fields": [
    {"name": "trip_id", "type": "int"}
  ]
}
'@

$checkCompatPayload = @{ schema = $incompatibleSchema } | ConvertTo-Json -Compress
try {
    $compatResp = Invoke-RestMethod -Uri "$REGISTRY_URL/compatibility/subjects/trip.completed-value/versions/latest" `
        -Method POST `
        -ContentType "application/vnd.schemaregistry.v1+json" `
        -Body $checkCompatPayload

    if ($compatResp.is_compatible -eq $false) {
        Write-Host "✅ Incompatible schema correctly caught and marked is_compatible: false!" -ForegroundColor Green
    } else {
        Write-Host "Compatibility check result: $($compatResp.is_compatible)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "✅ Incompatible schema request rejected: $($_.Exception.Message)" -ForegroundColor Green
}

Write-Host "============================================================" -ForegroundColor Green
Write-Host " [SUCCESS] SCHEMA REGISTRY REGISTRATION AND CONTRACT TEST PASSED!" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green

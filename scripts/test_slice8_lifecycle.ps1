Write-Host "=== Step 1: Driver sends GPS ==="
$loc = Invoke-RestMethod -Uri "http://localhost:8083/api/drivers/location" `
  -Method POST -ContentType "application/json" `
  -Body '{"driverId": "taxi_001", "lat": 33.5731, "lon": -7.5898, "speed": 35.0}'
$loc | ConvertTo-Json

Write-Host "`n=== Step 2: Rider requests trip ==="
$trip = Invoke-RestMethod -Uri "http://localhost:8082/api/trips/request" `
  -Method POST -ContentType "application/json" `
  -Body '{"riderId": "rider_1234", "originZone": 5, "destinationZone": 12}'
$trip | ConvertTo-Json
$tripId = $trip.tripId
Write-Host "Trip ID: $tripId"

Write-Host "`n=== Step 3: Wait for match (2s) ==="
Start-Sleep -Seconds 2
$s1 = Invoke-RestMethod "http://localhost:8082/api/trips/$tripId"
Write-Host "Status after match: $($s1.status)"

Write-Host "`n=== Step 4: Driver accepts trip ==="
$accept = Invoke-RestMethod -Uri "http://localhost:8083/api/drivers/trips/taxi_001/accept" -Method PUT
$accept | ConvertTo-Json
Start-Sleep -Seconds 1
$s2 = Invoke-RestMethod "http://localhost:8082/api/trips/$tripId"
Write-Host "Status after accept: $($s2.status)"

Write-Host "`n=== Step 5: Driver starts ride ==="
$start = Invoke-RestMethod -Uri "http://localhost:8083/api/drivers/trips/taxi_001/start" -Method PUT
$start | ConvertTo-Json
Start-Sleep -Seconds 1
$s3 = Invoke-RestMethod "http://localhost:8082/api/trips/$tripId"
Write-Host "Status after start: $($s3.status)"

Write-Host "`n=== Step 6: Driver completes ride ==="
$comp = Invoke-RestMethod -Uri "http://localhost:8083/api/drivers/trips/taxi_001/complete" -Method PUT
$comp | ConvertTo-Json
Start-Sleep -Seconds 1
$s4 = Invoke-RestMethod "http://localhost:8082/api/trips/$tripId"
Write-Host "Status after complete: $($s4.status)"

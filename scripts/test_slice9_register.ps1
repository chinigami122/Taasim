Write-Host "=== 1. Register User (soufiane@test.com) ==="
$body = @{
    email    = "soufiane@test.com"
    password = "secretPassword123"
    fullName = "Soufiane Bouziani"
    phone    = "+212600000000"
    role     = "DRIVER"
} | ConvertTo-Json

$res = Invoke-RestMethod -Uri "http://localhost:8081/api/auth/register" -Method POST -ContentType "application/json" -Body $body
$res | ConvertTo-Json

Write-Host "`n=== 2. Duplicate Registration Test ==="
try {
    Invoke-RestMethod -Uri "http://localhost:8081/api/auth/register" -Method POST -ContentType "application/json" -Body $body
} catch {
    Write-Host "Caught expected error: $($_.Exception.Message)"
    $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
    Write-Host "Error response body: $($reader.ReadToEnd())"
}

Write-Host "`n=== 3. Register Client User (client@test.com) ==="
$clientBody = @{
    email    = "client@test.com"
    password = "clientPassword456"
    fullName = "Karim Tazi"
    phone    = "+212611111111"
    role     = "CLIENT"
} | ConvertTo-Json

$clientRes = Invoke-RestMethod -Uri "http://localhost:8081/api/auth/register" -Method POST -ContentType "application/json" -Body $clientBody
$clientRes | ConvertTo-Json

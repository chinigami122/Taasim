Write-Host "=== 1. Register User (soufiane@test.com) ==="
$registerBody = @{
    email    = "soufiane@test.com"
    password = "password123"
    fullName = "Soufiane Bouziani"
    phone    = "+212600000000"
    role     = "DRIVER"
} | ConvertTo-Json

try {
    $regRes = Invoke-RestMethod -Uri "http://localhost:8081/api/auth/register" -Method POST -ContentType "application/json" -Body $registerBody
    Write-Host "Registered successfully:"
    $regRes | ConvertTo-Json
} catch {
    Write-Host "User already registered or error: $($_.Exception.Message)"
}

Write-Host "`n=== 2. Login User (Valid Credentials) ==="
$loginBody = @{
    email    = "soufiane@test.com"
    password = "password123"
} | ConvertTo-Json

$loginRes = Invoke-RestMethod -Uri "http://localhost:8081/api/auth/login" -Method POST -ContentType "application/json" -Body $loginBody
$loginRes | ConvertTo-Json

$token = $loginRes.accessToken
Write-Host "`n=== 3. Decode JWT Claims ==="
$parts = $token.Split('.')
$payload = $parts[1]
# Fix base64 padding
while ($payload.Length % 4 -ne 0) { $payload += '=' }
$decodedBytes = [System.Convert]::FromBase64String($payload)
$decodedJson = [System.Text.Encoding]::UTF8.GetString($decodedBytes)
Write-Host "Decoded JWT Payload:"
$decodedJson

Write-Host "`n=== 4. Test Invalid Password ==="
$badPasswordBody = @{
    email    = "soufiane@test.com"
    password = "wrongPassword"
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "http://localhost:8081/api/auth/login" -Method POST -ContentType "application/json" -Body $badPasswordBody
} catch {
    Write-Host "Caught expected 401 error: $($_.Exception.Message)"
    $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
    Write-Host "Error response body: $($reader.ReadToEnd())"
}

Write-Host "`n=== 5. Test Unknown User ==="
$unknownUserBody = @{
    email    = "unknown@test.com"
    password = "password123"
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "http://localhost:8081/api/auth/login" -Method POST -ContentType "application/json" -Body $unknownUserBody
} catch {
    Write-Host "Caught expected 401 error: $($_.Exception.Message)"
    $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
    Write-Host "Error response body: $($reader.ReadToEnd())"
}

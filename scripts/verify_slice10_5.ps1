# Verification script for Slice 10.5
$ErrorActionPreference = "Continue"

Write-Host "=== Test 1: Prod profile WITHOUT secrets (expected to FAIL) ==="
Remove-Item Env:\JWT_SECRET -ErrorAction SilentlyContinue
Remove-Item Env:\POSTGRES_PASSWORD -ErrorAction SilentlyContinue
$output1 = & java -jar backend\auth-service\target\auth-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod 2>&1
if ($output1 -match "Missing or default value for required prod env var: JWT_SECRET") {
    Write-Host "PASS: Prod profile failed loudly as expected!" -ForegroundColor Green
} else {
    Write-Host "FAIL: Did not see expected error message." -ForegroundColor Red
}

Write-Host "`n=== Test 2: Prod profile WITH valid secrets (expected to pass SecretValidator) ==="
$env:JWT_SECRET = "YkwqV8fgUpeawwix8jR4UzShtalnhzns5befUGPqeAs="
$env:POSTGRES_PASSWORD = "taasim"

$proc = Start-Process -FilePath "java" -ArgumentList "-jar", "backend\auth-service\target\auth-service-0.0.1-SNAPSHOT.jar", "--spring.profiles.active=prod", "--server.port=8089" -PassThru -NoNewWindow -RedirectStandardOutput "test_prod.log" -RedirectStandardError "test_prod_err.log"

Start-Sleep -Seconds 12

$logContent = ""
if (Test-Path "test_prod.log") {
    $logContent = Get-Content "test_prod.log" -Raw
}

Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue

if ($logContent -match "All required prod secrets present") {
    Write-Host "PASS: SecretValidator validated all required secrets successfully!" -ForegroundColor Green
} else {
    Write-Host "FAIL or still starting up. Log snippet:" -ForegroundColor Yellow
    Get-Content "test_prod.log" -Tail 20
    Get-Content "test_prod_err.log" -Tail 20
}

# Clean up temp logs
Remove-Item "test_prod.log" -ErrorAction SilentlyContinue
Remove-Item "test_prod_err.log" -ErrorAction SilentlyContinue

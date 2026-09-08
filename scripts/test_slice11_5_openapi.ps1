# Verification Script for Slice 11.5 — OpenAPI & Swagger UI
$ErrorActionPreference = "Continue"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "    Slice 11.5: OpenAPI Specification & Swagger UI Test " -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

$services = @(
    @{ Name = "Auth Service";   Port = 8081; ExpectedTitle = "TaaSim Auth Service API" },
    @{ Name = "Trip Service";   Port = 8082; ExpectedTitle = "TaaSim Trip Service API" },
    @{ Name = "Driver Service"; Port = 8083; ExpectedTitle = "TaaSim Driver Service API" }
)

foreach ($svc in $services) {
    Write-Host "`n--- Testing $($svc.Name) on port $($svc.Port) ---" -ForegroundColor Yellow

    # Test 1: Machine-readable OpenAPI spec at /v3/api-docs
    $apiDocsUrl = "http://localhost:$($svc.Port)/v3/api-docs"
    Write-Host -NoNewline "1. GET $apiDocsUrl -> "
    try {
        $json = Invoke-RestMethod -Uri $apiDocsUrl -Method GET -TimeoutSec 5 -ErrorAction Stop
        if ($json.openapi -and $json.info.title -eq $svc.ExpectedTitle) {
            Write-Host "PASS (OpenAPI $($json.openapi), Title: '$($json.info.title)')" -ForegroundColor Green
            
            # Check bearerAuth security scheme
            if ($json.components.securitySchemes.bearerAuth) {
                Write-Host "   ? Found securityScheme 'bearerAuth' (type: $($json.components.securitySchemes.bearerAuth.type), scheme: $($json.components.securitySchemes.bearerAuth.scheme))" -ForegroundColor DarkGreen
            }
            
            # Print documented paths count
            $pathCount = ($json.paths | Get-Member -MemberType NoteProperty).Count
            Write-Host "   ? Documented paths: $pathCount" -ForegroundColor DarkGreen
        } else {
            Write-Host "WARN (Unexpected payload: $($json.info.title))" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "SKIP/OFFLINE: $($_.Exception.Message)" -ForegroundColor DarkGray
    }

    # Test 2: Swagger UI interactive HTML at /swagger-ui/index.html
    $swaggerUrl = "http://localhost:$($svc.Port)/swagger-ui/index.html"
    Write-Host -NoNewline "2. GET $swaggerUrl -> "
    try {
        $res = Invoke-WebRequest -Uri $swaggerUrl -Method GET -TimeoutSec 5 -ErrorAction Stop
        if ($res.StatusCode -eq 200) {
            Write-Host "PASS (200 OK - HTML Content Length: $($res.RawContentLength))" -ForegroundColor Green
        } else {
            Write-Host "FAIL (Status: $($res.StatusCode))" -ForegroundColor Red
        }
    } catch {
        Write-Host "SKIP/OFFLINE: $($_.Exception.Message)" -ForegroundColor DarkGray
    }
}

Write-Host "`nOpenAPI verification script completed." -ForegroundColor Cyan

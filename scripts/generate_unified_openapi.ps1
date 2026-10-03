# generate_unified_openapi.ps1
# Fetches OpenAPI specs from TaaSim microservices and merges them into a unified spec.

$services = @(
    @{ Name = "auth"; Url = "http://localhost:8081/v3/api-docs" },
    @{ Name = "trip"; Url = "http://localhost:8082/v3/api-docs" },
    @{ Name = "driver"; Url = "http://localhost:8083/v3/api-docs" },
    @{ Name = "billing"; Url = "http://localhost:8086/v3/api-docs" }
)

Write-Host "Fetching OpenAPI specs from microservices..." -ForegroundColor Cyan

$unified = [ordered]@{
    openapi = "3.0.1"
    info = @{
        title = "TaaSim Platform API"
        description = "Unified OpenAPI specification for TaaSim ride-hailing platform"
        version = "1.0.0"
    }
    servers = @(
        @{ url = "http://localhost:8080"; description = "API Gateway" }
    )
    tags = @()
    paths = [ordered]@{}
    components = [ordered]@{
        schemas = [ordered]@{}
        securitySchemes = @{
            bearerAuth = @{
                type = "http"
                scheme = "bearer"
                bearerFormat = "JWT"
                description = "JWT Bearer token"
            }
        }
    }
}

foreach ($s in $services) {
    try {
        $spec = Invoke-RestMethod -Uri $s.Url -Method Get -TimeoutSec 5
        Write-Host "  -> Successfully fetched $($s.Name) spec" -ForegroundColor Green
        
        if ($spec.tags) {
            foreach ($t in $spec.tags) {
                $unified.tags += $t
            }
        }
        
        if ($spec.paths) {
            foreach ($prop in $spec.paths.PSObject.Properties) {
                $unified.paths[$prop.Name] = $prop.Value
            }
        }
        
        if ($spec.components -and $spec.components.schemas) {
            foreach ($schema in $spec.components.schemas.PSObject.Properties) {
                $unified.components.schemas[$schema.Name] = $schema.Value
            }
        }
    } catch {
        Write-Host "  -> Warning: Failed to fetch from $($s.Url): $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

$outputDir = Join-Path $PSScriptRoot "..\frontend\client-app"
if (-not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
}

$outputFile = Join-Path $outputDir "openapi.json"
$unified | ConvertTo-Json -Depth 20 | Set-Content -Path $outputFile -Encoding utf8
Write-Host "Unified OpenAPI spec written to $outputFile" -ForegroundColor Green

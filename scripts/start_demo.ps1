param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    if (-not $SkipBuild) {
        Push-Location backend
        try { mvn -B package -DskipTests; if ($LASTEXITCODE) { throw 'Backend build failed' } } finally { Pop-Location }
    }
    docker compose -f docker-compose.demo.yml --profile ui up -d --build --wait --wait-timeout 360
    if ($LASTEXITCODE) { throw 'Docker startup failed' }
    node scripts/wait_for_stack.mjs
    if ($LASTEXITCODE) { throw 'Stack readiness failed' }
    & "$PSScriptRoot/seed_demo.ps1"
    Write-Host 'Client http://localhost:5173 | Driver http://localhost:5174 | Admin http://localhost:5175'
} finally { Pop-Location }

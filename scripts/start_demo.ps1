param([switch]$SkipBuild, [switch]$Analytics)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    if (-not $SkipBuild) {
        Push-Location backend
        try { mvn -B package -DskipTests; if ($LASTEXITCODE) { throw 'Backend build failed' } } finally { Pop-Location }
    }
    $composeArgs = @('compose', '-f', 'docker-compose.yml', '-f', 'docker-compose.app.yml')
    if ($Analytics) { $composeArgs += @('--profile', 'analytics') }
    & docker @composeArgs up -d --build --wait --wait-timeout 600
    if ($LASTEXITCODE) { throw 'Docker startup failed' }
    node scripts/wait_for_stack.mjs
    if ($LASTEXITCODE) { throw 'Stack readiness failed' }
    & "$PSScriptRoot/seed_demo.ps1"
    Write-Host 'Client http://localhost:5173 | Driver http://localhost:5174 | Admin http://localhost:5175'
} finally { Pop-Location }

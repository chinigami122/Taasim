<#
.SYNOPSIS
  TaaSim Backend Developer Task Helper for PowerShell.
.EXAMPLE
  .\tasks.ps1 test
  .\tasks.ps1 build
  .\tasks.ps1 docker
  .\tasks.ps1 up
  .\tasks.ps1 down
  .\tasks.ps1 logs
#>

param (
    [Parameter(Position = 0, Mandatory = $true)]
    [ValidateSet("test", "verify", "build", "docker", "up", "down", "logs")]
    [string]$Task
)

$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $PSScriptRoot

switch ($Task) {
    "test" {
        mvn -B test
    }
    "verify" {
        mvn -B verify
    }
    "build" {
        mvn -B clean package -DskipTests
    }
    "docker" {
        docker compose -f ../docker-compose.backend.yml build
    }
    "up" {
        docker compose -f ../docker-compose.yml up -d
        docker compose -f ../docker-compose.backend.yml up -d
    }
    "down" {
        docker compose -f ../docker-compose.backend.yml down
        docker compose -f ../docker-compose.yml down
    }
    "logs" {
        docker compose -f ../docker-compose.backend.yml logs -f
    }
}

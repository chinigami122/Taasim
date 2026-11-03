param([string]$Base = "http://localhost:8080", [string]$Password = "DemoPassword123!")
$ErrorActionPreference = 'Stop'
$accounts = @(
    @{email='admin@taasim.demo'; role='ADMIN'; fullName='Demo Operations'},
    @{email='client@taasim.demo'; role='CLIENT'; fullName='Demo Passenger'},
    @{email='driver1@taasim.demo'; role='DRIVER'; fullName='Ahmed'},
    @{email='driver2@taasim.demo'; role='DRIVER'; fullName='Youssef'},
    @{email='driver3@taasim.demo'; role='DRIVER'; fullName='Karim'}
)
foreach ($account in $accounts) {
    $body = $account.Clone(); $body.password = $Password
    try { Invoke-RestMethod "$Base/api/auth/register" -Method Post -ContentType 'application/json' -Body ($body | ConvertTo-Json) | Out-Null }
    catch {
        # Only an existing account with the expected credentials is safe to reuse.
        Invoke-RestMethod "$Base/api/auth/login" -Method Post -ContentType 'application/json' -Body (@{email=$account.email; password=$Password} | ConvertTo-Json) | Out-Null
    }
    Write-Host "$($account.role): $($account.email)"
}
Write-Host 'Demo accounts ready. Use the Password argument supplied to this script (default documented in README).'

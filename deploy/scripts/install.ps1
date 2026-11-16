# First-time setup on a Windows store server (Windows 10/11 + Docker Desktop).
# Run in PowerShell from the StoreMIS folder:  powershell -ExecutionPolicy Bypass -File deploy\scripts\install.ps1
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host 'Docker Desktop is not installed. See docs\phase-9\deployment-guide.md, section 3.'; exit 1
}

function New-Secret([int]$length) {
    $chars = [char[]]'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    $bytes = New-Object byte[] $length
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })
}

if (-not (Test-Path .env)) {
    $admin = 'Admin' + (New-Secret 8) + '7'
    (Get-Content .env.example) `
        -replace '^DB_PASSWORD=.*', ('DB_PASSWORD=' + (New-Secret 32)) `
        -replace '^JWT_SECRET=.*', ('JWT_SECRET=' + (New-Secret 64)) `
        -replace '^ADMIN_PASSWORD=.*', ('ADMIN_PASSWORD=' + $admin) |
        Set-Content -Encoding ascii .env
    Write-Host 'Created deploy\.env with random secrets.'
    Write-Host "First sign-in:  user 'admin'  password '$admin'  (you will be asked to change it)"
} else {
    Write-Host 'deploy\.env already exists; keeping it.'
}

New-Item -ItemType Directory -Force backups, backups-external | Out-Null
docker compose up -d --build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host 'Waiting for Store MIS to start ...'
for ($i = 0; $i -lt 60; $i++) {
    $state = docker inspect -f '{{.State.Health.Status}}' storemis-web-1 2>$null
    if ($state -eq 'healthy') { break }
    Start-Sleep -Seconds 5
}
docker compose ps
$ip = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -notmatch 'Loopback|vEthernet|WSL' -and $_.IPAddress -notlike '169.*' } | Select-Object -First 1).IPAddress
Write-Host "Store MIS is running. Open http://$ip/ on the shop PCs."
Write-Host 'Allow the port in Windows Firewall if other PCs cannot connect (see the deployment guide).'

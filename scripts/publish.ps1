# Publish script: build APK, generate version.json, optionally serve over LAN.
param(
    [int]$VersionCode = 2,
    [string]$VersionName = "1.1",
    [int]$Port = 8000,
    [string]$Notes = "New update available",
    [switch]$StartServer
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = "F:\Android\jdk-17.0.20+8"
$env:GRADLE_USER_HOME = "F:\Android\.gradle"

# Ensure the port is free, auto-increment if occupied
function Test-PortFree([int]$p) {
    $existing = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue
    return -not $existing
}
while (-not (Test-PortFree $Port)) {
    Write-Warning "Port $Port is in use, trying next port..."
    $Port++
}

# 1. Build debug APK
Write-Host "== Building APK =="
Push-Location $root
& ".\gradlew.bat" assembleDebug --no-daemon --console=plain | Out-Host
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "Build failed" }
Pop-Location

# 2. Copy APK to publish dir
$publishDir = Join-Path $root "publish"
New-Item -ItemType Directory -Path $publishDir -Force | Out-Null
$apkSource = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
Copy-Item $apkSource (Join-Path $publishDir "app-debug.apk") -Force

# 3. Detect LAN IPv4 candidates (skip virtual adapters)
$candidates = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object {
        ($_.IPAddress -like "192.168.*" -or $_.IPAddress -like "10.*" -or $_.IPAddress -like "172.*") -and
        $_.IPAddress -notlike "169.254.*" -and
        $_.InterfaceAlias -notmatch "Loopback|vEthernet|VMware|VirtualBox|WSL|Tailscale|ZeroTier|Hamachi|Npcap|TAP"
    } |
    Sort-Object InterfaceMetric |
    Select-Object -ExpandProperty IPAddress

if (-not $candidates) {
    Write-Warning "No LAN IP detected. Using 192.168.1.100 as placeholder."
    $ip = "192.168.1.100"
} else {
    $ip = $candidates | Select-Object -First 1
    Write-Host "LAN IP candidates: $($candidates -join ', ')"
}

# 4. Write version.json (UTF-8 without BOM)
$json = @{
    versionCode = $VersionCode
    versionName = $VersionName
    updateNotes = $Notes
    downloadUrl = "http://${ip}:${Port}/app-debug.apk"
    force       = $false
} | ConvertTo-Json
$jsonPath = Join-Path $publishDir "version.json"
[System.IO.File]::WriteAllText($jsonPath, $json, (New-Object System.Text.UTF8Encoding($false)))

Write-Host ""
Write-Host "== Publish ready =="
Write-Host "Publish dir : $publishDir"
Write-Host "Update URL  : http://${ip}:${Port}/version.json"
Write-Host "APK URL     : http://${ip}:${Port}/app-debug.apk"

# 5. Optional: start local HTTP server in background
if ($StartServer) {
    $python = (Get-Command python -ErrorAction SilentlyContinue).Source
    if ($python) {
        Start-Process -FilePath $python -ArgumentList "-m", "http.server", "$Port", "-d", $publishDir -WindowStyle Hidden
        Write-Host "HTTP server started on port $Port (background). Stop with: Stop-Process -Name python"
    } else {
        Write-Host "python not found. Run manually: python -m http.server $Port -d $publishDir"
    }
}

Write-Host ""
Write-Host "On phone: Settings -> Update -> set update URL to:"
Write-Host "  http://${ip}:${Port}/version.json"
Write-Host "Phone must be on the same WiFi as this PC."

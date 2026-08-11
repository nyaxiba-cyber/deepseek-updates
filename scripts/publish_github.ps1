# Publish to GitHub Releases: build APK, upload APK + version.json, create release.
param(
    [Parameter(Mandatory = $true)][int]$VersionCode,
    [Parameter(Mandatory = $true)][string]$VersionName,
    [string]$Notes = "New update",
    [string]$Repo = "nyaxiba-cyber/deepseek-updates"
)

$ErrorActionPreference = "Stop"
$gh = "C:\Program Files\GitHub CLI\gh.exe"
$root = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = "F:\Android\jdk-17.0.20+8"
$env:GRADLE_USER_HOME = "F:\Android\.gradle"

Write-Host "== Building APK v$VersionName (versionCode $VersionCode) =="
Push-Location $root
& ".\gradlew.bat" assembleDebug --no-daemon --console=plain | Out-Host
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "Build failed" }
Pop-Location

$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
$staging = Join-Path $root "publish_github"
New-Item -ItemType Directory -Path $staging -Force | Out-Null
Copy-Item $apk (Join-Path $staging "app-debug.apk") -Force

$apkName = "app-debug-v$VersionName.apk"
Copy-Item $apk (Join-Path $staging $apkName) -Force

$json = @{
    versionCode = $VersionCode
    versionName = $VersionName
    updateNotes = $Notes
    downloadUrl = "https://cdn.jsdelivr.net/gh/$Repo@main/publish/$apkName"
    force       = $false
} | ConvertTo-Json
$jsonPath = Join-Path $staging "version.json"
[System.IO.File]::WriteAllText($jsonPath, $json, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "== Publishing release v$VersionName to $Repo =="
try {
    & $gh release delete "v$VersionName" --repo $Repo --yes --cleanup-tag 2>$null | Out-Null
} catch {
    Write-Host "No existing release v$VersionName, creating fresh."
}
& $gh release create "v$VersionName" `
    (Join-Path $staging $apkName) `
    $jsonPath `
    --repo $Repo `
    --title "v$VersionName" `
    --notes $Notes `
    --latest
if ($LASTEXITCODE -ne 0) { throw "Release failed" }

# 5. 同步到仓库 main 分支 publish/ 目录（jsDelivr CDN 从仓库文件分发）
Write-Host "== Syncing publish/ to repo main branch =="
$tmp = Join-Path $env:TEMP "publish_repo_$PID"
if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
& $gh auth setup-git 2>$null
$proxyArgs = @("-c", "http.proxy=http://127.0.0.1:7897", "-c", "https.proxy=http://127.0.0.1:7897")
try { git @proxyArgs clone "https://github.com/$Repo.git" $tmp 2>&1 | Out-Null } catch { }
$publishInRepo = Join-Path $tmp "publish"
if (-not (Test-Path $publishInRepo)) { New-Item -ItemType Directory -Path $publishInRepo -Force | Out-Null }
Copy-Item (Join-Path $staging $apkName) $publishInRepo -Force
Copy-Item $jsonPath $publishInRepo -Force
Get-ChildItem $publishInRepo -Filter "app-debug-v*.apk" |
    Where-Object { $_.Name -ne $apkName } |
    Remove-Item -Force
Push-Location $tmp
try { git config user.name $Repo.Split('/')[0] 2>&1 | Out-Null } catch { }
try { git config user.email "$($Repo.Split('/')[0])@users.noreply.github.com" 2>&1 | Out-Null } catch { }
try { git @proxyArgs add -A 2>&1 | Out-Null } catch { }
try { git @proxyArgs commit -m "publish v$VersionName" 2>&1 | Out-Null } catch { }
try { git @proxyArgs push origin main 2>&1 | Out-Null } catch { }
if ($LASTEXITCODE -ne 0) { Write-Warning "git sync may have failed (exit $LASTEXITCODE)" }
Pop-Location
Remove-Item $tmp -Recurse -Force

Write-Host ""
Write-Host "== Published =="
Write-Host "Update URL: https://raw.githubusercontent.com/$Repo/main/publish/version.json"
Write-Host "APK URL   : https://cdn.jsdelivr.net/gh/$Repo@main/publish/$apkName"

# 一键发布到蒲公英：本地构建 debug APK -> 复制到 publish/ -> 上传蒲公英
# 前提：
#   1) publish/pgyer_keys.json 已存在：{"api_key": "你的APIKey", "app_key": "可选"}
#      （API Key 在 pgyer.com 后台「账户设置 -> API 信息」；App Key 在「应用管理 -> 安装设置」）
#   2) publish/update_notes.txt 已存在（UTF-8，写本次更新说明）
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$env:JAVA_HOME = "F:\Android\jdk-17.0.20+8"
$env:GRADLE_USER_HOME = "F:\Android\.gradle"

# 从 build.gradle.kts 读取版本
$gradle = Get-Content -Raw -Encoding UTF8 "app\build.gradle.kts"
$versionCode = [regex]::Match($gradle, 'versionCode\s*=\s*(\d+)').Groups[1].Value
$versionName = [regex]::Match($gradle, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
Write-Host "构建 v$versionName (versionCode $versionCode) ..."

& .\gradlew.bat assembleDebug --no-daemon
if ($LASTEXITCODE -ne 0) { Write-Error "构建失败"; exit 1 }

$apk = Join-Path $root "publish\app-debug-v$versionName.apk"
Copy-Item (Join-Path $root "app\build\outputs\apk\debug\app-debug.apk") $apk -Force

$notesFile = Join-Path $root "publish\update_notes.txt"
$keysFile = Join-Path $root "publish\pgyer_keys.json"
if (-not (Test-Path $keysFile)) {
    Write-Error "缺少 $keysFile，请先创建（格式见脚本注释）"
    exit 1
}
if (-not (Test-Path $notesFile)) {
    [System.IO.File]::WriteAllText(
        $notesFile,
        "v$versionName 更新内容请写在这里",
        (New-Object System.Text.UTF8Encoding($false))
    )
    Write-Host "已创建 $notesFile，请填写更新说明后重新运行本脚本"
    exit 0
}

$env:PYTHONIOENCODING = "utf-8"
python (Join-Path $root "scripts\publish_pgyer.py") $apk $notesFile $keysFile
exit $LASTEXITCODE

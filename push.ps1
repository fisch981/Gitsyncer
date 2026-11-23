param (
    [string]$ChangeSummary = "Automatisches Update",
    [string]$BumpType = "patch" # patch, minor, major
)

$ErrorActionPreference = "Stop"

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)

# 1. Versionierung in app/build.gradle.kts auslesen & inkrementieren
$gradleFile = Join-Path $PSScriptRoot "app\build.gradle.kts"
$content = [System.IO.File]::ReadAllText($gradleFile, [System.Text.Encoding]::UTF8)

if ($content -match 'versionCode\s*=\s*(\d+)') {
    $currentCode = [int]$matches[1]
    $newCode = $currentCode + 1
} else {
    $newCode = 2
}

if ($content -match 'versionName\s*=\s*"([^"]+)"') {
    $currentName = $matches[1]
    $parts = $currentName.Split('.')
    $major = [int]$parts[0]
    $minor = [int]$parts[1]
    $patch = [int]$parts[2]

    if ($BumpType -eq "major") {
        $major++; $minor = 0; $patch = 0
    } elseif ($BumpType -eq "minor") {
        $minor++; $patch = 0
    } else {
        $patch++
    }
    $newName = "$major.$minor.$patch"
} else {
    $newName = "1.0.1"
}

Write-Host "==> Erhöhe Version: $currentName (Code $currentCode) -> $newName (Code $newCode)" -ForegroundColor Cyan
$content = $content -replace 'versionCode\s*=\s*\d+', "versionCode = $newCode"
$content = $content -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$newName`""
[System.IO.File]::WriteAllText($gradleFile, $content, $utf8NoBom)

# 2. CHANGELOG.md aktualisieren
$changelogFile = Join-Path $PSScriptRoot "CHANGELOG.md"
$today = (Get-Date).ToString("yyyy-MM-dd")
$newEntry = @"
## [$newName] - $today

### Geändert / Hinzugefügt
- $ChangeSummary

"@

$existingChangelog = [System.IO.File]::ReadAllText($changelogFile, [System.Text.Encoding]::UTF8)
# Nach dem Header einfügen
if ($existingChangelog -match '(?s)(# Changelog.*?---\s*\r?\n\r?\n)(.*)') {
    $updatedChangelog = $matches[1] + $newEntry + "`n" + $matches[2]
    [System.IO.File]::WriteAllText($changelogFile, $updatedChangelog, $utf8NoBom)
    Write-Host "==> Changelog für v$newName aktualisiert" -ForegroundColor Cyan
}

# 3. Release APK kompilieren
Write-Host "==> Kompiliere Release-APK v$newName..." -ForegroundColor Cyan
$gradleBat = Join-Path $PSScriptRoot "gradlew.bat"
if (Test-Path $gradleBat) {
    & $gradleBat assembleRelease --no-daemon
} elseif (Get-Command gradle -ErrorAction SilentlyContinue) {
    gradle assembleRelease --no-daemon
} elseif (Test-Path "C:\Users\docker\AppData\Local\AndroidBuildTools\gradle\gradle-8.9\bin\gradle.bat") {
    & "C:\Users\docker\AppData\Local\AndroidBuildTools\gradle\gradle-8.9\bin\gradle.bat" assembleRelease --no-daemon
} else {
    throw "Weder gradlew.bat noch 'gradle' im Pfad gefunden!"
}

$apkFile = Join-Path $PSScriptRoot "app\build\outputs\apk\release\Gitsyncer-release.apk"
if (-not (Test-Path $apkFile)) {
    $fallbackApk = Join-Path $PSScriptRoot "app\build\outputs\apk\release\app-release.apk"
    if (Test-Path $fallbackApk) {
        $apkFile = $fallbackApk
    } else {
        Write-Error "Release APK konnte nicht unter $apkFile erstellt werden!"
    }
}

# 4. Git Stage, Commit & Push
Write-Host "==> Stage & Commit..." -ForegroundColor Cyan
git add -A
git commit -m "Release v${newName}: $ChangeSummary"
if ($LASTEXITCODE -ne 0) {
    throw "git commit fehlgeschlagen!"
}

Write-Host "==> Pushe nach GitHub (origin main)..." -ForegroundColor Cyan
git push origin main
if ($LASTEXITCODE -ne 0) {
    throw "git push origin main fehlgeschlagen!"
}

Write-Host "==> Veröffentliche Release-APK auf GitHub Releases (v$newName)..." -ForegroundColor Cyan
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
gh release view "v$newName" 2>$null | Out-Null
$releaseExists = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = $prevEap
if ($releaseExists) {
    gh release upload "v$newName" "$apkFile#Gitsyncer-release.apk" --clobber
} else {
    gh release create "v$newName" "$apkFile#Gitsyncer-release.apk" --title "Gitsyncer v$newName" --notes "$ChangeSummary"
}
if ($LASTEXITCODE -ne 0) {
    throw "GitHub Release Upload für v$newName fehlgeschlagen!"
}

Write-Host "==> Erfolgreich veröffentlicht & gepusht: v$newName (Code $newCode)" -ForegroundColor Green

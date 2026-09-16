<#
.SYNOPSIS
  Writes the update manifest that Album checks at runtime.

.DESCRIPTION
  version.properties is the single source of truth for the version number. This
  script turns it into the JSON document the app downloads, so the release
  checklist never has to hand-edit a version number.

  Publish the result as a release asset named album-update.json on the release
  tagged v<version>; gradle.properties points ALBUM_UPDATE_URL at
  .../releases/latest/download/album-update.json, which always resolves to the
  newest published manifest.

  The manifest format is the one AppUpdateChecker reads:

    {"versionCode":171,"versionName":"1.2.1","downloadUrl":"https://...","notes":"..."}

.EXAMPLE
  .\scripts\write-update-manifest.ps1 -Notes "Fix the update channel"
#>
[CmdletBinding()]
param(
    [string]$Repository = "Tnomlav/ALbum",
    [string]$Notes = "",
    [string]$OutputPath
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$versionFile = Join-Path $root "version.properties"
if (-not (Test-Path -LiteralPath $versionFile)) {
    throw "version.properties not found at $versionFile"
}

$version = @{}
foreach ($line in Get-Content -LiteralPath $versionFile) {
    if ($line -match '^\s*([A-Z_]+)\s*=\s*(.+?)\s*$') {
        $version[$Matches[1]] = $Matches[2]
    }
}

$major = $version["VERSION_MAJOR"]
$minor = $version["VERSION_MINOR"]
$patch = $version["VERSION_PATCH"]
$code = $version["VERSION_CODE"]
if (-not $major -or -not $minor -or -not $patch -or -not $code) {
    throw "version.properties must define VERSION_CODE, VERSION_MAJOR, VERSION_MINOR and VERSION_PATCH"
}

$versionName = "$major.$minor.$patch"
$tag = "v$versionName"
$downloadUrl = "https://github.com/$Repository/releases/download/$tag/Album-$tag.apk"

if (-not $OutputPath) {
    $OutputPath = Join-Path $root "app\release\album-update.json"
}
$outputDirectory = Split-Path -Parent $OutputPath
if (-not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}

$manifest = [ordered]@{
    versionCode = [int]$code
    versionName = $versionName
    downloadUrl = $downloadUrl
    notes       = $Notes
}

$json = $manifest | ConvertTo-Json -Depth 4
# The app parses the body with org.json; a byte order mark would break it.
[System.IO.File]::WriteAllText($OutputPath, $json, [System.Text.UTF8Encoding]::new($false))

Write-Host "Update manifest written to $OutputPath"
Write-Host "  versionCode : $code"
Write-Host "  versionName : $versionName"
Write-Host "  downloadUrl : $downloadUrl"
Write-Host ""
Write-Host "Next: attach this file to the $tag release as album-update.json."

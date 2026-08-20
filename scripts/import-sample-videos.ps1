param(
    [string]$Serial = ""
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$sampleDirectory = Join-Path $projectRoot "dev-assets\videos"
$remoteDirectory = "/sdcard/Movies/AlbumSamples"
$appFolderDirectory = Join-Path $projectRoot "dev-assets\app-folders"

function Find-Adb {
    $pathCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($pathCommand) {
        return $pathCommand.Source
    }

    $sdkCandidates = @(
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME,
        (Join-Path $env:LOCALAPPDATA "Android\Sdk")
    ) | Where-Object { $_ }

    foreach ($sdk in $sdkCandidates) {
        $candidate = Join-Path $sdk "platform-tools\adb.exe"
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }

    throw "adb was not found. Install Android SDK Platform-Tools or add adb to PATH."
}

$adb = Find-Adb
$videos = @(Get-ChildItem -LiteralPath $sampleDirectory -Filter "*.mp4" -File)
if ($videos.Count -eq 0) {
    throw "No MP4 samples found in $sampleDirectory"
}

$appFolders = @(
    @{ Local = "WeChat"; Remote = "/sdcard/Pictures/WeiXin" },
    @{ Local = "QQ"; Remote = "/sdcard/Pictures/QQ" },
    @{ Local = "Pixiv"; Remote = "/sdcard/Pictures/Pixiv" }
)

$adbArguments = @()
if ($Serial) {
    $adbArguments += @("-s", $Serial)
}

& $adb @adbArguments "get-state" | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "No usable Android device found. Start an emulator or connect a device."
}

& $adb @adbArguments "shell" "mkdir" "-p" $remoteDirectory
if ($LASTEXITCODE -ne 0) {
    throw "Could not create $remoteDirectory on the device."
}

foreach ($video in $videos) {
    $remotePath = "$remoteDirectory/$($video.Name)"
    Write-Host "Importing $($video.Name)..."
    & $adb @adbArguments "push" $video.FullName $remotePath
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to copy $($video.Name)."
    }

    & $adb @adbArguments "shell" "am" "broadcast" `
        "-a" "android.intent.action.MEDIA_SCANNER_SCAN_FILE" `
        "-d" "file://$remotePath" | Out-Null
}

$imageCount = 0
foreach ($folder in $appFolders) {
    $localDirectory = Join-Path $appFolderDirectory $folder.Local
    $images = @(Get-ChildItem -LiteralPath $localDirectory -File | Where-Object {
        $_.Extension -in ".jpg", ".jpeg", ".png", ".webp"
    })

    & $adb @adbArguments "shell" "mkdir" "-p" $folder.Remote
    if ($LASTEXITCODE -ne 0) {
        throw "Could not create $($folder.Remote) on the device."
    }

    foreach ($image in $images) {
        $remotePath = "$($folder.Remote)/$($image.Name)"
        Write-Host "Importing $($folder.Local)/$($image.Name)..."
        & $adb @adbArguments "push" $image.FullName $remotePath
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to copy $($image.Name)."
        }

        & $adb @adbArguments "shell" "am" "broadcast" `
            "-a" "android.intent.action.MEDIA_SCANNER_SCAN_FILE" `
            "-d" "file://$remotePath" | Out-Null
        $imageCount++
    }
}

Write-Host "Imported $($videos.Count) videos and $imageCount app-folder images."

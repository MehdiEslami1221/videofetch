# VideoFetch local tooling installer for Windows x64.
# Run from the project root (or any working directory).
# Downloads are SHA-256 verified before being copied to tools/bin.
# Does not modify the system PATH or require administrator access.

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$toolsDir = Join-Path $root 'tools\bin'
$tmpDir = Join-Path $root 'tools\.setup-temp'

New-Item -ItemType Directory -Force -Path $toolsDir, $tmpDir | Out-Null

function Download-File {
    param([string]$Url, [string]$Destination)
    Invoke-WebRequest -Uri $Url -OutFile $Destination -UseBasicParsing
    if (-not (Test-Path -LiteralPath $Destination -PathType Leaf)) {
        throw "Download did not create a file: $Url"
    }
}

function Assert-GnuSha256 {
    param([string]$FilePath, [string]$ManifestPath, [string]$FileName)

    # Some versions of PowerShell expose application/octet-stream response content
    # as byte[], so always read the downloaded manifest from disk as UTF-8.
    $manifest = [System.IO.File]::ReadAllText($ManifestPath, [System.Text.Encoding]::UTF8)
    $escapedName = [regex]::Escape($FileName)
    $match = [regex]::Match($manifest, "(?im)^([0-9a-f]{64})[ \t]+\*?$escapedName[ \t]*$")
    if (-not $match.Success) {
        throw "The checksum manifest has no SHA-256 entry for $FileName. Verify the download URL and retry."
    }

    $expected = $match.Groups[1].Value.ToLowerInvariant()
    $actual = (Get-FileHash -LiteralPath $FilePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $expected) {
        throw "SHA-256 mismatch for $FileName. The file was not installed."
    }
}

function Assert-SimpleSha256 {
    param([string]$FilePath, [string]$ManifestPath)
    $manifest = [System.IO.File]::ReadAllText($ManifestPath, [System.Text.Encoding]::UTF8)
    $match = [regex]::Match($manifest, '(?i)\b[0-9a-f]{64}\b')
    if (-not $match.Success) {
        throw "FFmpeg checksum was not found in downloaded .sha256 file."
    }
    $expected = $match.Value.ToLowerInvariant()
    $actual = (Get-FileHash -LiteralPath $FilePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $expected) {
        throw "FFmpeg SHA-256 mismatch. The archive was not installed."
    }
}

try {
    Write-Host 'Downloading yt-dlp for Windows x64...'
    $ytTemp = Join-Path $tmpDir 'yt-dlp.exe'
    $ytManifest = Join-Path $tmpDir 'SHA2-256SUMS'
    Download-File 'https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe' $ytTemp
    Download-File 'https://github.com/yt-dlp/yt-dlp/releases/latest/download/SHA2-256SUMS' $ytManifest
    Assert-GnuSha256 $ytTemp $ytManifest 'yt-dlp.exe'
    $ytInstalled = Join-Path $toolsDir 'yt-dlp.exe'
    Copy-Item -LiteralPath $ytTemp -Destination $ytInstalled -Force
    Write-Host 'yt-dlp checksum verified.' -ForegroundColor Green

    Write-Host 'Downloading FFmpeg Essentials for Windows x64...'
    $ffZip = Join-Path $tmpDir 'ffmpeg-release-essentials.zip'
    $ffChecksum = Join-Path $tmpDir 'ffmpeg-release-essentials.zip.sha256'
    Download-File 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip' $ffZip
    Download-File 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip.sha256' $ffChecksum
    Assert-SimpleSha256 $ffZip $ffChecksum

    $extractDir = Join-Path $tmpDir 'expanded'
    Expand-Archive -LiteralPath $ffZip -DestinationPath $extractDir -Force
    $ffmpeg = Get-ChildItem -LiteralPath $extractDir -Recurse -Filter 'ffmpeg.exe' -File | Select-Object -First 1
    $ffprobe = Get-ChildItem -LiteralPath $extractDir -Recurse -Filter 'ffprobe.exe' -File | Select-Object -First 1
    if (-not $ffmpeg -or -not $ffprobe) {
        throw 'The verified FFmpeg archive does not contain ffmpeg.exe and ffprobe.exe.'
    }
    Copy-Item -LiteralPath $ffmpeg.FullName -Destination (Join-Path $toolsDir 'ffmpeg.exe') -Force
    Copy-Item -LiteralPath $ffprobe.FullName -Destination (Join-Path $toolsDir 'ffprobe.exe') -Force

    # Do not pipe a native process into Select-Object -First 1: that can
    # prematurely close the process output stream and produce a non-zero exit code.
    & $ytInstalled --version
    $ytExitCode = $LASTEXITCODE
    if ($ytExitCode -ne 0) { throw "yt-dlp version check failed with exit code $ytExitCode." }

    & (Join-Path $toolsDir 'ffmpeg.exe') -version
    $ffExitCode = $LASTEXITCODE
    if ($ffExitCode -ne 0) { throw "FFmpeg version check failed with exit code $ffExitCode." }

    Write-Host 'All tools installed and verified in tools/bin.' -ForegroundColor Green
    Write-Host 'Restart your Spring Boot application, then check GET /api/system/tools.' -ForegroundColor Green
}
finally {
    if (Test-Path -LiteralPath $tmpDir) {
        Remove-Item -LiteralPath $tmpDir -Recurse -Force
    }
}

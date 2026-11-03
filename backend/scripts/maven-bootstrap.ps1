# Bootstraps Maven in the project directory; does not require an admin install.
param([Parameter(ValueFromRemainingArguments=$true)][string[]]$MavenArgs)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$version = '3.9.11'
$base = Join-Path $root '.tools'
$distribution = Join-Path $base "apache-maven-$version"
$executable = Join-Path $distribution 'bin\mvn.cmd'
$expectedSha512 = '03e2d65d4483a3396980629f260e25cac0d8b6f7f2791e4dc20bc83f9514db8d0f05b0479e699a5f34679250c49c8e52e961262ded468a20de0be254d8207076'

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'Java 21 JDK is required. Install JDK 21 and configure JAVA_HOME.'
}
if (-not (Test-Path $executable)) {
    New-Item -ItemType Directory -Force -Path $base | Out-Null
    $archive = Join-Path $base "apache-maven-$version-bin.zip"
    $uri = "https://archive.apache.org/dist/maven/maven-3/$version/binaries/apache-maven-$version-bin.zip"
    Write-Host "Downloading Apache Maven $version (first run only)..."
    Invoke-WebRequest -Uri $uri -OutFile $archive -UseBasicParsing
    $actual = (Get-FileHash -Algorithm SHA512 $archive).Hash.ToLowerInvariant()
    if ($actual -ne $expectedSha512) {
        Remove-Item -Force $archive
        throw 'Maven SHA-512 verification failed. Installation aborted.'
    }
    Expand-Archive -LiteralPath $archive -DestinationPath $base -Force
    Remove-Item -Force $archive
}
Push-Location $root
try {
    & $executable @MavenArgs
    exit $LASTEXITCODE
} finally {
    Pop-Location
}

# Build/lint only. No ADB connection or installation is performed here.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_.$/:-]+$')]
    [string]$InputId,
    [ValidatePattern('^$|^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$')]
    [string]$TvPackage = ''
)
$ErrorActionPreference = 'Stop'
$repoDir = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$wrapper = Join-Path $repoDir 'launcher/gradlew.bat'
if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) { throw 'The repository launcher Gradle wrapper is missing.' }
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
    throw 'Set JAVA_HOME to a full JDK 17 before building the PS5 shortcut.'
}
$savedAndroid = $env:ANDROID_HOME
Push-Location $PSScriptRoot
try {
    if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    if (-not (Test-Path -LiteralPath (Join-Path $env:ANDROID_HOME 'platforms/android-34/android.jar'))) {
        throw 'Android SDK platform 34 is missing from ANDROID_HOME.'
    }
    & $wrapper --no-daemon -p $PSScriptRoot "-Pps5InputId=$InputId" "-Pps5TvPackage=$TvPackage" assembleRelease lintRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'PS5 shortcut build or lint failed.' }
    $apk = Join-Path $PSScriptRoot 'app/build/outputs/apk/release/app-release.apk'
    Write-Host "Built PS5 shortcut: $apk"
    Write-Host "SHA256: $((Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash)"
} finally {
    Pop-Location
    $env:ANDROID_HOME = $savedAndroid
}

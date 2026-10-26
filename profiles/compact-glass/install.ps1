# Build the repository launcher and apply this profile to one already authorized Android TV.
# No packages are disabled. See README.md before using this on a different TV.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$TvHost,
    [string]$Ps5InputId = '',
    [ValidatePattern('^$|^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$')]
    [string]$Ps5TvPackage = '',
    [switch]$DisableScreensaver,
    [switch]$EnableNowPlaying
)

$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$profileDir = $PSScriptRoot
$repoDir = (Resolve-Path (Join-Path $profileDir '../..')).Path
$python = Join-Path $repoDir 'tools/venv/Scripts/python.exe'
$tvTool = Join-Path $repoDir 'tools/tv.py'
$pushTool = Join-Path $repoDir 'tools/b64push.py'
$homeQuery = 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'

if (-not (Test-Path -LiteralPath $python -PathType Leaf)) {
    throw 'Create tools/venv and install tools/requirements.txt first; see this profile README.'
}
if (-not (Test-Path -LiteralPath (Join-Path $profileDir 'config.json') -PathType Leaf)) {
    throw 'This profile is incomplete: config.json is missing.'
}
if ($Ps5TvPackage -and -not $Ps5InputId) { throw '-Ps5TvPackage requires -Ps5InputId.' }
if ($Ps5InputId -and $Ps5InputId -notmatch '^[A-Za-z0-9_.$/:-]+$') {
    throw 'Pass the complete TV input ID from dumpsys tv_input, without spaces or shell characters.'
}

function Invoke-Tv([string]$Command) {
    $result = @(& $python -I $tvTool $Command)
    if ($LASTEXITCODE -ne 0) { throw "TV command failed: $Command" }
    return ($result -join "`n").Trim()
}

function Invoke-ProfileScript([string]$Path, [hashtable]$Parameters) {
    # The repository scripts change their working directory. Restore it after each call,
    # including a throw, and reject an unchecked native-command failure in a child script.
    Push-Location $repoDir
    try {
        & $Path @Parameters
        $scriptSucceeded = $?
        $scriptExitCode = $LASTEXITCODE
        if (-not $scriptSucceeded -or $scriptExitCode -ne 0) {
            throw "Profile step failed: $Path (native exit code $scriptExitCode)."
        }
    } finally {
        Pop-Location
    }
}

function Get-HomeComponent {
    $answer = Invoke-Tv $homeQuery
    $component = $answer -split '\r?\n' | Where-Object { $_ -match '\S' } | Select-Object -Last 1
    if (-not $component -or $component.Trim() -notmatch '^[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+$') { return '' }
    return $component.Trim()
}

function Assert-LauncherVisible {
    $activities = Invoke-Tv 'dumpsys activity activities'
    if ($activities -notmatch '(?m)^\s*(mFocusedApp|mResumedActivity|topResumedActivity)[^\r\n]*com\.gathin\.tvhome/') {
        throw 'The launcher is not the focused/resumed activity. Inspect the TV before continuing.'
    }
}

$savedHost = $env:TV_HOST
$savedPort = $env:TV_PORT
$savedAdb = $env:TV_ADB_BINARY
$savedAndroid = $env:ANDROID_HOME
$savedJava = $env:JAVA_HOME
Push-Location $repoDir
try {
    $env:TV_HOST = $TvHost
    # Use exactly the supplied endpoint. Do not inherit another session's nonstandard port.
    $env:TV_PORT = $null
    if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    if (-not $env:TV_ADB_BINARY) {
        $sdkAdb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
        if (Test-Path -LiteralPath $sdkAdb -PathType Leaf) { $env:TV_ADB_BINARY = $sdkAdb }
        else {
            $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
            if (-not $adbCommand) { throw 'Install Google platform-tools and pair/connect this TV first.' }
            $env:TV_ADB_BINARY = $adbCommand.Source
        }
    }

    $sdkText = Invoke-Tv 'getprop ro.build.version.sdk'
    $deviceSdk = 0
    if (-not [int]::TryParse($sdkText, [ref]$deviceSdk)) { throw 'Could not read the TV Android SDK level.' }
    if ($deviceSdk -lt 26) { throw "This TV is SDK $deviceSdk; this source requires SDK 26 or newer." }
    $currentUser = Invoke-Tv 'am get-current-user'
    if ($currentUser -ne '0') { throw 'The repository installer supports the primary Android user (0) only.' }

    $homeBefore = Get-HomeComponent
    if (-not $homeBefore) { throw 'Could not determine the current Home component.' }
    if ($Ps5InputId) {
        $inputs = Invoke-Tv 'dumpsys tv_input'
        $inputPattern = '(?<![A-Za-z0-9_.$/:-])' + [regex]::Escape($Ps5InputId) + '(?![A-Za-z0-9_.$/:-])'
        if ($inputs -notmatch $inputPattern) {
            throw 'The requested PS5 input ID was not present in this TV input inventory. Verify it on this TV first.'
        }
        if ($Ps5TvPackage) {
            $tvPackagePath = Invoke-Tv "pm path $Ps5TvPackage"
            if ($tvPackagePath -notmatch '(?m)^package:') { throw 'The requested PS5 TV application is not installed.' }
        }
    }

    # These files contain private device settings, so keep them under the existing ignored backup/ tree.
    $backupDir = Join-Path $repoDir ('backup/compact-glass-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    New-Item -ItemType Directory -Path $backupDir | Out-Null
    $backupCommands = [ordered]@{
        'packages.txt' = 'pm list packages -f'
        'disabled-packages.txt' = 'pm list packages -d'
        'getprop.txt' = 'getprop'
        'settings-system.txt' = 'settings list system'
        'settings-secure.txt' = 'settings list secure'
        'settings-global.txt' = 'settings list global'
    }
    foreach ($entry in $backupCommands.GetEnumerator()) {
        Invoke-Tv $entry.Value | Out-File -FilePath (Join-Path $backupDir $entry.Key) -Encoding utf8
    }
    $homeBefore | Out-File -FilePath (Join-Path $backupDir 'home-before.txt') -Encoding utf8
    Write-Host "Saved device inventory in $backupDir"
    if ($DisableScreensaver) {
        Invoke-Tv 'settings put secure screensaver_enabled 0' | Out-Null
        if ((Invoke-Tv 'settings get secure screensaver_enabled') -ne '0') {
            throw 'The TV did not accept disabling its screensaver.'
        }
    }

    # Keep the repository's existing Home checks and explicitly keep the stock launcher enabled.
    try {
        Invoke-ProfileScript (Join-Path $repoDir 'launcher/build-and-install.ps1') @{
            TvHost = $TvHost
            StockLauncher = 'none'
        }
    } catch {
        $installError = $_
        # Some firmware ignores set-home-activity. Continue only for that specific outcome,
        # after checking an installed, visible launcher and a working existing Home owner.
        if ($installError.Exception.Message -notmatch "^Home resolves to '") { throw }
        $homeAfter = Get-HomeComponent
        if (-not $homeAfter -or $homeAfter -like 'com.gathin.tvhome/*') { throw $installError }
        $launcherPath = Invoke-Tv 'pm path com.gathin.tvhome'
        if ($launcherPath -notmatch '(?m)^package:') { throw $installError }
        Assert-LauncherVisible
        Write-Warning "The firmware kept Home assigned to $homeAfter. The new launcher is installed and open. Choose it in the TV default-app settings and verify the real Home button; the stock launcher remains enabled."
    }

    if ($Ps5InputId) {
        Invoke-ProfileScript (Join-Path $profileDir 'console-shortcut/build.ps1') @{
            InputId = $Ps5InputId
            TvPackage = $Ps5TvPackage
        }
        $shortcutApk = Join-Path $profileDir 'console-shortcut/app/build/outputs/apk/release/app-release.apk'
        & $python -I $pushTool $shortcutApk '/data/local/tmp/cyt-ps5-shortcut.apk'
        if ($LASTEXITCODE -ne 0) { throw 'PS5 shortcut upload failed.' }
        $installResult = Invoke-Tv 'pm install -r /data/local/tmp/cyt-ps5-shortcut.apk'
        if ($installResult -notmatch '(?m)^Success\s*$') { throw "PS5 shortcut installation failed: $installResult" }
        Invoke-Tv 'rm -f /data/local/tmp/cyt-ps5-shortcut.apk' | Out-Null
    } else {
        Write-Host 'PS5 shortcut build/install skipped: no -Ps5InputId supplied.'
    }

    Invoke-ProfileScript (Join-Path $repoDir 'tools/theme.ps1') @{
        Theme = $profileDir
        TvHost = $TvHost
    }
    Assert-LauncherVisible
    $homeFinal = Get-HomeComponent
    $campfireAsset = Join-Path $profileDir 'media/campfire.mp4'
    if (Test-Path -LiteralPath $campfireAsset -PathType Leaf) {
        & $python -I $pushTool $campfireAsset '/data/local/tmp/cyt-campfire.mp4'
        if ($LASTEXITCODE -ne 0) { throw 'Campfire upload failed.' }
        Invoke-Tv 'content write --uri content://com.gathin.tvhome.theme/campfire.mp4 < /data/local/tmp/cyt-campfire.mp4' | Out-Null
    }
    if ($EnableNowPlaying) {
        Invoke-Tv 'cmd notification allow_listener com.gathin.tvhome/.NowPlayingAccess' | Out-Null
        & $python -I (Join-Path $repoDir 'tools/theme_restart.py')
        if ($LASTEXITCODE -ne 0) { throw 'Could not restart Home after enabling media access.' }
        Write-Host 'Enabled local media-session access for the Now playing badge.'
    }
    $report = [ordered]@{
        profile = 'compact-glass'
        installedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
        sdk = $deviceSdk
        homeBefore = $homeBefore
        homeAfter = $homeFinal
        launcherPackage = 'com.gathin.tvhome'
        launcherApkSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $repoDir 'launcher/app/build/outputs/apk/release/app-release.apk')).Hash
        profileConfigSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $profileDir 'config.json')).Hash
        ps5ShortcutInstalled = [bool]$Ps5InputId
        screensaverDisabled = [bool]$DisableScreensaver
        nowPlayingAccessEnabled = [bool]$EnableNowPlaying
        packagesDisabledByThisScript = @()
    }
    if ($Ps5InputId) { $report.ps5ApkSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $shortcutApk).Hash }
    $report | ConvertTo-Json -Depth 4 | Out-File -FilePath (Join-Path $backupDir 'install-report.json') -Encoding utf8
    if ($homeFinal -notlike 'com.gathin.tvhome/*') {
        Write-Warning "Profile applied, but Home still resolves to $homeFinal. Default Home selection needs a check on the TV."
    }
    Write-Host 'Verify the real remote, Inputs, PS5 (if installed), streaming playback, and a reboot on this TV.'
    Write-Host 'Debugging settings and authorizations have been left unchanged.'
} finally {
    Pop-Location
    $env:TV_HOST = $savedHost
    $env:TV_PORT = $savedPort
    $env:TV_ADB_BINARY = $savedAdb
    $env:ANDROID_HOME = $savedAndroid
    $env:JAVA_HOME = $savedJava
}

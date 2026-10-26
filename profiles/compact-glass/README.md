# Compact glass TV profile

This folder preserves the personal launcher setup as source that can be reviewed and reused on another Android TV. Keep it together with this repository's modified `launcher/` and `tools/`; copying only this folder does not provide the launcher implementation.

`config.json` holds the appearance, dock order, app names/icons, and hidden apps. Build-time dimensions, focus choices, and the More folder selection are in `../../launcher/app/src/main/res/values/personal_tv.xml`. The wallpaper files beside it hold the reusable backgrounds. The optional `console-shortcut/` project builds a PS5 tile for an input verified on the destination TV. `manifest.json` lists exactly what the installer includes.

The installer builds and updates `com.gathin.tvhome`, applies this folder through `tools/theme.ps1`, and optionally builds and installs `com.local.grundig.playstation`. It installs no streaming apps, SmartTube, remapping tools, downloaders, or other third-party applications. A YouTube label/icon in the profile refers to an already installed SmartTube app; it does not replace that application's package or install Google YouTube.

The profile hides selected app tiles from the main screen. Hiding an app does not disable its services or block its network traffic. The installer disables no packages, removes no apps, enables no remote-key helpers, and changes no DNS settings. A debloat list from one TV is never automatically applied to another TV.

## Saved appearance

- A centered app area 480 dp wide, reduced by 25% from the previous 640 dp layout, with one favorites row at the bottom. Down reveals more rows; Up returns to the compact dock.
- No app names or reserved name space under Home tiles, including during focus. The More window retains app names and a Back button.
- A glass panel around all visible app rows, a clear focus outline without a broad hover glow, and a small triangular glass arrow below the dock.
- A soft-colored three-dot More tile inside the app grid. Settings and Inputs keep their icons; the Inputs menu sorts connected HDMI inputs before names.
- An explicit favorites order with the PS5 shortcut in slot five when installed. This order is saved in configuration rather than inferred from usage on a different TV.

The twelve saved wallpaper JPEGs are 3557 to 3840 pixels wide (3K/4K source assets), with hashes and public source URLs in [wallpapers.sources.json](wallpapers.sources.json). Wallpapers use 32-bit color (`ARGB_8888`) and are decoded for the launcher's native surface. On a 1920 by 1080 launcher surface, output is native 1080p; supplying a 4K image does not change the TV's display resolution. Source and license information stays in the repository, with no wallpaper-credit text on Home.

## Requirements

- Android TV / Google TV with SDK 26 (Android 8.0) or newer, using primary user 0.
- Windows PowerShell, Python, JDK 17, Android SDK platform 34 and build-tools 34.0.0. Set `JAVA_HOME` to the full JDK and `ANDROID_HOME` to the SDK if they are not already configured.
- Google platform-tools with this exact TV already paired and connected. For Wireless debugging, use the current connection port, which differs from the pairing port. See [native ADB instructions](../../tools/native-adb.md).
- The repository Python environment, created from the repository root:

```powershell
python -m venv tools\venv
tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
```

## Install

Review `config.json` for the destination TV first. Package names, the Live TV application, remote mappings, app availability, and HDMI input IDs vary by TV. Read [PROMPT.md](../../PROMPT.md) for the device checks and recovery procedure before making changes.

From the repository root, after pairing/connecting Google ADB:

```powershell
.\profiles\compact-glass\install.ps1 -TvHost '<tv-ip>:<connection-port>'
```

For a TV using regular network ADB on port 5555, a host without the port is accepted. The script requires the host every time and never saves one into the reusable profile. It uses the Google ADB backend (`TV_ADB_BINARY`, the SDK's `adb.exe`, or `adb` on PATH) and only selects an already authorized transport.

Before installing, the script checks the Android SDK and saves packages, disabled packages, system properties, settings, and the original Home component under the ignored `backup/compact-glass-<timestamp>/` folder. It writes an installation report there with APK/config hashes. These backups can contain private values; keep them local.

The existing launcher installer is always called with `-StockLauncher none`. On firmware that ignores the requested default Home, this profile can still be installed and opened while the existing Home remains available. The script warns that default Home selection needs checking; verify the real Home button and reboot behavior before relying on it. Each nested build/install/theme step restores its working directory and stops if it reports an error.

## Optional PS5 tile

Find the destination TV's input ID without opening a factory or service menu:

```powershell
$env:TV_ADB_BINARY = 'adb'
$env:TV_HOST = '<tv-ip>:<connection-port>'
tools\venv\Scripts\python.exe -I tools\tv.py 'dumpsys tv_input'
```

Verify which ID represents the physical port connected to the PS5, then pass that complete ID:

```powershell
.\profiles\compact-glass\install.ps1 -TvHost '<tv-ip>:<connection-port>' -Ps5InputId '<verified-input-id>'
```

If the TV requires its own TV app to handle the standard input intent, also pass `-Ps5TvPackage '<verified-tv-package>'`. Without it, Android resolves the standard TV input intent. The installer checks that the input ID exists and, when specified, that the TV app package is installed. It cannot infer which attached device is a PS5; verify the actual switch with the remote.

Without `-Ps5InputId`, no shortcut APK is built or installed. An existing tile is left as installed; the launcher only shows an app that is present. The PS5 source includes a generic vector controller/console icon; any personal tile artwork belongs in the profile configuration.

The shortcut requests no Android permissions. It has one Leanback launcher activity, no Home intent, no services, no accessibility integration, no network access, and no telemetry. It sends a standard TV input intent and immediately closes.

Build only the shortcut, without connecting to or changing a TV:

```powershell
.\profiles\compact-glass\console-shortcut\build.ps1 -InputId '<verified-input-id>'
```

The helper reuses `launcher/gradlew.bat` and Android Gradle plugin 8.5.2. Its generated APK is `console-shortcut/app/build/outputs/apk/release/app-release.apk`. Generated builds and APKs are ignored. Both apps use the local Android debug signing key, matching the repository build convention. Updating an existing installation from another computer requires the same signing key; a different key causes Android to reject the update. Never commit signing keys or ADB credentials.

## Change or undo

The triangular music button starts Spotify Liked Songs by default (verified on Spotify TV 1.83.3). Hold OK on that button to save a Daily Mix or playlist link for this TV. That account-specific choice stays in private on-device preferences and is not included in the public profile. Clearing the field explicitly resumes the current queue instead. Spotify must already be installed and signed in.

The clock is 30% smaller with a short date underneath. Pass `-DisableScreensaver` to the installer to reproduce the original TV's disabled Google idle photo screen. This changes only `secure screensaver_enabled` to `0`; sleep/power settings remain unchanged. Its previous value is saved in the settings backup. To undo, restore that value (normally `settings put secure screensaver_enabled 1`).

The More window shows the currently displayed wallpaper's photographer, licence, and Wikimedia source URL below its apps. Home stays free of attribution text. See `CREDITS.md` and `wallpapers.sources.json` for the complete reusable collection metadata.

Inputs sorts connected HDMI sources first. A small green dot at the Inputs button's upper-right corner appears whenever Android reports a visible external source connected, and updates on connection changes.

The dock hides after 30 seconds without interaction or when focus enters the top bar, leaving a glass up-arrow. Only a wake-up press after actual inactivity is consumed, including repeats and key release. Moving around the top bar works immediately; Down returns to the dock immediately. Volume and playback keys retain their normal behavior. A small location name appears at bottom right for six seconds when Home opens or the wallpaper changes; full credits remain in More.

The gear button opens a settings popup with a Campfire On/Off toggle. Campfire loops `media/campfire.mp4` locally and hides every UI element after 10 seconds of inactivity. A navigation press reveals the UI without activating anything. The preference persists on that TV. The video has no audio track, requests no audio focus, and stops decoding when Home is backgrounded, allowing Spotify to continue independently. Turning Campfire off restores the wallpaper. The installer copies the clip alongside the theme; see `media/CREDITS.md` for its licence. This is a reusable licensed log-fire clip, not a download of the suggested YouTube video.

Campfire keeps the screen awake while it is visible; switching it off or leaving Home releases that request without changing system sleep settings.

The small Now playing bubble beneath the top buttons shows local media-session artwork, track, and artist only while playback is active. Pass `-EnableNowPlaying` to grant Android notification-listener access to `com.gathin.tvhome/.NowPlayingAccess`, which permits media-session observation. The implementation never reads notification contents, sends playback commands, fetches artwork URLs, or stores track history. It needs no Spotify credentials. Revoke with `cmd notification disallow_listener com.gathin.tvhome/.NowPlayingAccess`; the bubble then stays hidden. Some apps do not expose artwork or metadata, so the bubble falls back to a play icon or application name.

Edit `dock_apps` in `config.json` to change the chosen app order, with the PS5 package at position five when that tile is used. The saved order is explicit; usage history from one TV is not copied to other TVs. App package names in the hidden/More lists must be reviewed on each destination device. Change the wallpaper collection in this folder to change the backgrounds; source/license metadata stays in the repository and need not appear on Home.

Reapply the profile with `tools/theme.ps1 -TvHost '<tv-ip>:<connection-port>' -Theme profiles/compact-glass`. Reset the theme with the same script and `-Reset`. To return Home to the stock launcher, choose it in the TV's default-app settings; this installer leaves it enabled. It does not restore a previously installed APK version or remove the optional shortcut. Use Android Settings to manage an app you no longer want.

If you separately disable packages after inspecting a particular TV, keep the exact list and backup for that TV and use `tools/restore.ps1` to restore that list. Do not infer that an app hidden by this profile has been disabled.

The installer leaves debugging settings and authorizations unchanged. Keep debugging available for further updates until you choose to close it. When you do choose to close it, turn off USB/Network/Wireless debugging and revoke authorizations on the TV. Check the current connection port with `tools/venv/Scripts/python.exe tools/adb-closed.py '<tv-ip>:<connection-port>'` before rebooting, then confirm debugging stays off after reboot.

## Reviewed cleanup on the original Grundig TV

`grundig-debloat.txt` records the nine packages disabled on the original Grundig Android 11 / ARCELIK sinchon setup: Google Feedback, Google Partner Setup, partner automatic installation configuration, Assistant, Play Games, TED, stock Home, official YouTube, and YouTube Music. The stock Home recommendation/ad feed is no longer displayed; SmartTube supplies the YouTube tile. No DNS-wide ad blocking was applied, and ads inside other streaming applications are not covered by this cleanup.

This list is an audit record for that specific setup. The portable installer never applies it automatically. Review every package and input/remote dependency on another TV, back it up, and use the repository's guarded disable/restore tools if a matching cleanup is requested. The utility apps inside More remain enabled.

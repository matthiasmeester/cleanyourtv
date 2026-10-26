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

The online collection contains **106 day photographs and 114 night photographs**, including landscapes, cities, musicians, concert photography, archival scenes and spaceflight. Each listed source is at least **2560 × 1440**; no image was upscaled or artificially darkened. Find every location, author, licence, original source and available story in [the illustrated catalogue](catalog.html), [CSV index](catalog.csv), [day manifest](wallpapers.sources.json), and [night manifest](night-wallpapers.sources.json). The illustrated catalogue loads Wikimedia images over the internet as you scroll; it is not an offline gallery.

The TV retains only a bounded **100,000,000-byte image cache**, rather than installing the full collection. A small offline seed set is listed in `manifest.json`; the complete remote index is `wallpapers/catalog.tsv`. Cached images remain available without internet, but unseen photographs cannot download while offline. The limit concerns image files; application code, metadata and decoded image memory are separate. The browser catalogue is also separate from the TV cache and may transfer more than 100 MB if you browse every photograph.

Most catalogue entries were downloaded and checked with Pillow during curation. Entries marked `download_verified: false` have source-reported dimensions only and no invented checksum; the TV must validate their actual format and dimensions before displaying them. Recorded SHA-256 hashes identify verified source bytes. Photos keep their individual licences, separate from this repository's MIT licence. The original 014.jpg panorama is excluded because its height was below 1440 pixels.

This profile opts into downloading photographs from Wikimedia HTTPS image hosts. Those hosts receive the TV's IP address, requested image URLs and request timing; that necessarily reveals which photographs were requested. No Spotify credentials, personal location, separate viewing-history upload, or analytics are required. The downloaded file itself is subject to the cache limit. Full-size research downloads are private local material, not part of the public repository or an installation.

This personal fork deliberately adds `android.permission.INTERNET` for on-demand wallpaper downloads, unlike the upstream local-only launcher. Downloads require no API key and allow only `upload.wikimedia.org` and `thumb.wikimedia.org`. Cached files live in `wallpaper-cache`; each transfer is limited to 20,000,000 bytes. The included four seed images total 11,732,325 bytes and count toward the overall 100,000,000-byte image budget. These installation and network details are recorded explicitly in `manifest.json`.

Home briefly shows a short location label; full source and licence information stays in More. Source resolution does not change the TV display resolution: a 1080p launcher surface still renders at 1080p. To reduce effects on modest TV hardware, this profile disables row fades and advanced effects and enables reduced motion.

The day and night collections contain selected photographs, not generated or manually painted scenes. Night uses real nighttime photographs rather than darkened copies of daytime images. With no location configured, the switch uses 19:00–07:00 in the TV's time zone; this is a time-based fallback, not local sunset detection. Configuring the launcher's latitude and longitude enables its existing sunrise/sunset calculation; personal coordinates are not included in this public profile.

The night collection also includes illuminated late-blue-hour city scenes, dark concert stages and astronomical/space photography. Historical photographs with genuinely unknown venue information say so rather than guessing. Political rallies, gore and explicit material are excluded.

## Rebuild the photo catalogue

The two source manifests are the canonical reusable collection. Run `python profiles/compact-glass/build_catalog.py` to regenerate the online index, location captions, More credits, HTML/CSV catalogue, mood lists and manifest counts. Run the same command with `--check` to check for stale outputs without changing them. Pillow is required to validate any locally present seed JPEGs; the generator never downloads, resizes or recompresses photographs. Reinstallation uses these public manifests and the small seed set, not private staging files. Review the installer and network behaviour before installing on another TV.

Validation requires the local image set to match `offline_seed_files` exactly and stay at or below 20,000,000 bytes, preventing accidental inclusion of the full research collection. It also rejects uncatalogued images, unsafe download URLs, duplicate source photographs and non-permitted licences. Run the offline safety tests with `python -m unittest discover -s profiles/compact-glass -p test_catalog.py`.

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

The dock hides when focus enters the top bar. After 30 seconds without interaction, the dock and top buttons hide together. Back also dismisses the top bar/app section when nothing remains to go back to. A wake-up Up press focuses the first top-bar button; Left/Right/Down/OK wakes to the first dock app. Only the wake-up navigation press is consumed, including repeats and key release; dedicated app shortcuts, volume, and playback keys retain their normal behavior. Moving around the top bar works immediately, and Down returns to the dock. A small location name appears at bottom right for six seconds when Home opens, the wallpaper changes, or the UI wakes; full credits remain in More.

The gear button opens a settings popup with Campfire On/Off directly below normal Settings. Campfire loops locally and hides every UI element after 10 seconds of inactivity. A navigation press reveals the UI without activating anything. The On/Off preference and separate volume both persist on that TV across launcher restarts and reboots. Playback requests no audio focus and stops decoding when Home is backgrounded, allowing Spotify to continue independently. Turning Campfire off restores the wallpaper. The bundled `media/campfire.mp4` fallback has no audio track; see `media/CREDITS.md` for its licence. It is a reusable licensed log-fire clip, not a download of the suggested YouTube video.

Campfire keeps the screen awake while it is visible; switching it off or leaving Home releases that request without changing system sleep settings.

To install your own local MP4, pass `-CampfireVideo 'C:\path\to\video.mp4'`. Personal video files are not committed. The installer uses ffmpeg from PATH to amplify custom audio by 3×, limiting peaks to avoid clipping, and copies the video stream unchanged. Its generated `campfire-adjusted.mp4` stays in the private backup folder. Pass `-CampfireAudioGain 1` to preserve the original audio without ffmpeg, or choose another gain from 1 to 8. This processing never changes system or Spotify volume.

The bottom of the settings popup shows a separate campfire-volume control only while Campfire is on: Left/Right changes it by 5%, from 0–100%, saved on this TV. It defaults to muted. A custom video must contain audio for this control to produce sound; the bundled licensed fallback is silent.

The selected fireplace visuals are Wikimedia's `Kaminfeuer.webm`, already reproduced by the bundled H.264 fallback. The original is 1280×720 and silent (the 1440p minimum applies to the photo catalogue). To combine these visuals with your own already-amplified audio without re-encoding the video:

```powershell
ffmpeg -i profiles/compact-glass/media/campfire.mp4 -stream_loop -1 -i private-audio.mp4 -map 0:v:0 -map 1:a:0 -c:v copy -c:a aac -b:a 192k -t 10.76 -movflags +faststart private-fireplace.mp4
```

Install that private result using `-CampfireVideo` and `-CampfireAudioGain 1` to avoid amplifying twice. Keep personal media outside Git. An optional UTF-8 `campfire-custom.txt` in the TV theme directory can provide custom source credits (up to 4096 bytes); an empty marker retains the generic personal-video label. Replacing media does not reset the TV's saved on/off or volume preferences.

The compact Now playing bubble at bottom left shows the track and artist only while playback is active, without artwork or a logo. Pass `-EnableNowPlaying` to grant Android notification-listener access to `com.gathin.tvhome/.NowPlayingAccess`, which permits media-session observation. The implementation never reads notification contents, sends playback commands, fetches artwork URLs, or stores track history. It needs no Spotify credentials. Revoke with `cmd notification disallow_listener com.gathin.tvhome/.NowPlayingAccess`; the bubble then stays hidden. Missing metadata falls back to the application name.

Edit `dock_apps` in `config.json` to change the chosen app order, with the PS5 package at position five when that tile is used. The saved order is explicit; usage history from one TV is not copied to other TVs. App package names in the hidden/More lists must be reviewed on each destination device. Change the wallpaper collection in this folder to change the backgrounds; source/license metadata stays in the repository and need not appear on Home.

Reapply the profile with `tools/theme.ps1 -TvHost '<tv-ip>:<connection-port>' -Theme profiles/compact-glass`. Reset the theme with the same script and `-Reset`. To return Home to the stock launcher, choose it in the TV's default-app settings; this installer leaves it enabled. It does not restore a previously installed APK version or remove the optional shortcut. Use Android Settings to manage an app you no longer want.

If you separately disable packages after inspecting a particular TV, keep the exact list and backup for that TV and use `tools/restore.ps1` to restore that list. Do not infer that an app hidden by this profile has been disabled.

The installer leaves debugging settings and authorizations unchanged. Keep debugging available for further updates until you choose to close it. When you do choose to close it, turn off USB/Network/Wireless debugging and revoke authorizations on the TV. Check the current connection port with `tools/venv/Scripts/python.exe tools/adb-closed.py '<tv-ip>:<connection-port>'` before rebooting, then confirm debugging stays off after reboot.

## Reviewed cleanup on the original Grundig TV

`grundig-debloat.txt` records the nine packages disabled on the original Grundig Android 11 / ARCELIK sinchon setup: Google Feedback, Google Partner Setup, partner automatic installation configuration, Assistant, Play Games, TED, stock Home, official YouTube, and YouTube Music. The stock Home recommendation/ad feed is no longer displayed; SmartTube supplies the YouTube tile. No DNS-wide ad blocking was applied, and ads inside other streaming applications are not covered by this cleanup.

This list is an audit record for that specific setup. The portable installer never applies it automatically. Review every package and input/remote dependency on another TV, back it up, and use the repository's guarded disable/restore tools if a matching cleanup is requested. The utility apps inside More remain enabled.

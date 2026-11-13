# Y1 Music

A fast, minimal music player for the **Innioasis Y1** (Android 4.2.2, click wheel). It replaces the stock and Rockbox software and boots straight into your library.

Browse by artist or album, shuffle, play. Nothing else.

## Features
- Wheel + center button control (touch ignored)
- Artists, Albums, Shuffle All, Now Playing, Settings
- FLAC (up to 48 kHz), mp3, m4a, with cover art
- Resumes the last song, paused, after a reboot
- Backlight dims then turns off while playing (center wakes it); Bluetooth via Android's own settings
- Menu in about 9.5 s after boot (target was 20 s)

## How it's built
Plain Java, no libraries, no Gradle. One APK, a handful of files in `app/src/com/y1/music/`. It runs as the system user (signed with the public AOSP test platform key) so it can read the SD card, scans tags itself with `MediaMetadataRetriever`, and caches the library.

## Build and install
```
./build.sh        # build, sign, adb install, launch
```
Needs the Android SDK (`~/Android/Sdk`), the AOSP platform test key in `~/.android/aosp/`, and the Y1 on `adb`. Music goes in `/storage/sdcard0/Music`.

## Make it the launcher
Press Home and pick **Y1 Music** with **Always**. Optionally `./pkgs.sh disable` turns off 20 unused system apps.

## Recovery
```
adb uninstall com.y1.music     # stock player is home again
./pkgs.sh enable                  # re-enable disabled apps
```
Back to Rockbox (needs `backups/org.rockbox.apk` and `backups/Generic.kl.orig`, kept locally):
```
adb root; adb shell mount -o remount,rw /system
adb push backups/org.rockbox.apk /system/app/
adb push backups/Generic.kl.orig /system/usr/keylayout/Generic.kl
adb shell mount -o remount,ro /system; adb reboot
```
More in `NOTES.md` and `CONTEXT.md`.

## Status
Phases 0 to 9 done. Phase 10 (hardening, soak test) is next.

# Innioasis Y1: project context

## Goal
Replace the slow Rockbox/stock software on the Innioasis Y1 with a lean custom music app: music, Bluetooth, album covers, nothing else.

## Decision
No full custom firmware. Kernel, MTK bootloader, drivers and Bluetooth stack are months of work with brick risk. Instead: one small Android app (set as the launcher), plus disabling unused system apps.

## Device facts (observed over adb)
- Serial `0123456789ABCDEF`, product `Y1`, brand Timmkoo, manufacturer alps (MediaTek).
- Android 4.2.2, SDK 17, build JDQ39. `ro.debuggable=1`, `adb root` works.
- Stock player: `com.innioasis.y1` (`/data/app`, 58MB). Slow, too many features.
- Bluetooth: MediaTek stack, `com.mediatek.bluetooth` (`/system/app/MtkBt.apk`). Use Android's standard Bluetooth APIs; don't touch the stack.
- About 40 unneeded system packages (live wallpapers, FM radio, Exchange, calendar, telephony, SIM toolkit, TTS, etc.) are candidates to disable.
- Input devices: `mtk-kpd` (buttons), `mtk-tpd` (touch panel; the scroll wheel reports through this one as KEY_UP/KEY_DOWN, codes 103/108, and the center button as 232/353).
- Keylayouts live in `/system/usr/keylayout/`. `mtk-tpd` has no `.kl` of its own, so it falls back to `Generic.kl`.

## Done so far
1. Backed up `org.rockbox.apk`, `com.innioasis.y1-1.apk`, `MtkBt.apk` to `backups/`.
2. Found Rockbox was an Android app in `/system/app` running on top of Android (cause of slowness).
3. Stock player had been disabled; re-enabled it.
4. Wheel didn't work in the stock player. Cause: `Generic.kl` was a copy of `Rockbox.kl`. Fix: copied `Stock.kl` over `Generic.kl` (via `adb root`, remount `/system` rw, then ro). Original saved as `backups/Generic.kl.orig`. Wheel works after reboot.
5. Removed `/system/app/org.rockbox.apk`. Music and the `.rockbox` folder on the SD card were left alone.

## Revert commands
- Restore Rockbox: `adb root; adb shell mount -o remount,rw /system; adb push backups/org.rockbox.apk /system/app/; adb shell mount -o remount,ro /system; adb reboot`
- Restore Rockbox key layout: push `backups/Generic.kl.orig` to `/system/usr/keylayout/Generic.kl` the same way.

## Plan (next)
1. Decompile the stock app (rea) to learn how it reads music/art and handles the wheel.
2. Build one screen: song list, now-playing with cover, play/pause/next/previous. `MediaPlayer`, no libraries.
3. Bluetooth via the system settings screen for v1.
4. Install as the launcher so the Y1 boots into it.
5. Disable unused system apps to free RAM.

## Open question
Building needs the Android SDK at `~/Android/Sdk`. The home-folder rules say not to read it. Awaiting the user's call: use only its build tools, or install a separate toolchain.

## Notes
- `backups/*.apk` is gitignored (large, third-party binaries). `Generic.kl.orig` is tracked.
- Nothing has been committed or pushed yet.

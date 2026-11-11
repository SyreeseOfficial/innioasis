# Innioasis Y1 Music App: PRD (v1)

## Goal
A fast, minimal music player for the Y1 that replaces the stock/Rockbox software. Browse by artist or album, shuffle, play. Nothing else.

## Non-goals (v1)
Playlists, favorites, repeat, search, equalizer, sleep timer, all-songs list, progress bar/time, lyrics, radio, gapless, custom Bluetooth UI, custom firmware.

## Approach
One Android app (API 17, Android 4.2.2) set as the launcher. Plain `MediaPlayer`, no libraries. Unused system apps are disabled with `pm disable` (reversible). Kernel, bootloader and Bluetooth stack are untouched.

## Target library
- Source: `~/Music` on the PC, copied to the 128 GB SD card over USB. 3,312 songs, 86 GB, 90 artist folders.
- Formats: 3,227 FLAC, 71 mp3, 14 m4a.
- Names (artist, album, title, track) come from file tags via Android's media scanner.
- Cover art is embedded in the tags. Only 7 cover image files exist.

## Controls
- Wheel (scroll) and center button (select). Touch is ignored.
- Physical keys (verified): wheel clockwise = DPAD_RIGHT (108), counter-clockwise = DPAD_LEFT (103), center = ENTER (232), play/pause = MEDIA_PLAY_PAUSE, next = MEDIA_NEXT, previous = MEDIA_PREVIOUS, back = BACK. Full table in NOTES.md.
- Wired and Bluetooth headphone media keys: wired is in v1. Bluetooth is in only if it works with the MtkBt stack, otherwise dropped.

## Screens
### Home menu
Artists, Albums, Shuffle All, Now Playing, Settings.

### Lists (Artists, Albums, songs)
- Plain A-Z, no letter jumping. Albums inside an artist are alphabetical.
- Song rows show track number + title.
- Selecting a song queues the rest of that album/artist list in order.
- Shuffle is available for All, and within an artist or album.

### Now Playing
- Cover (if enabled), title, artist, album, battery level, shuffle indicator.
- No progress bar or time.
- Wheel changes volume.
- Previous restarts the song if more than 3 seconds in, otherwise goes to the previous song.

### Settings
- Bluetooth (opens Android's settings screen)
- Show covers on/off (default on)
- Rescan library
- Screen brightness
- Launch Android (escape to the normal Android UI)

## Behavior
- **Resume:** remembers song, position and queue. After boot or reopen it shows Now Playing paused until you press play.
- **Screen:** dims to the minimum (about 12%, Android's floor) after 15 seconds while playing; any wheel or button press brings it back (decided: no real screen-off, the wheel has no WAKE flag in Generic.kl and /system stays untouched). Music keeps playing.
- **Bad files:** skip to the next song and show a brief "can't play X" message. Log it.
- **Look:** dark, high contrast, large text.

## Performance targets
- Menus respond instantly.
- A song starts within 1 second.
- Boots to the menu in under 20 seconds.

## Risks (test first, on the device)
1. **FLAC playback** on Android 4.2.2 may stutter or fail on some files. Fallback: skip failures (decided). Test with a few hi-res tracks from the library.
2. **Embedded covers in FLAC** may not be readable through `MediaMetadataRetriever` on 4.2. If not, covers are limited to mp3/m4a or dropped.
3. **Library scan time** for 3,300 files. Needs a cached library so startup stays fast.
4. **Bluetooth headphone buttons** depend on the MtkBt stack.
5. **Launcher crash loop:** if the app crashes on boot, recover over `adb` (always available: `ro.debuggable=1`).

## Build order
1. Decompile the stock app (rea) to learn how it reads music, art and the wheel.
2. Map the physical keys with `adb getevent`.
3. Spike: play one FLAC and read one embedded cover on the device. Decides risks 1 and 2.
4. Library scan + Artists/Albums lists.
5. Now Playing, queue, shuffle, resume.
6. Settings screen.
7. Set as launcher.
8. Disable unused system apps.
9. One script: build, `adb install`, relaunch.

## Tooling
Android SDK at `~/Android/Sdk` (build tools and platform-tools allowed). Deploy with `adb`.

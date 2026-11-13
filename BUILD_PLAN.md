# Y1 Music App: Build Plan

Rules for every phase:
- One phase at a time. Each ends with a **TEST** (on the device) and a **SAVE** (git commit).
- Don't start the next phase until the TEST passes. If it fails, fix or log it in `NOTES.md`.
- Commit messages: `phaseN: short summary`. Tag a known-good build: `git tag vN-name`.
- No libraries. Plain Java, plain `MediaPlayer`, plain Android views. Build with SDK tools, no Gradle.

Layout (kept small, grows only when needed):
```
app/
  AndroidManifest.xml
  src/com/y1/music/*.java
  res/
build.sh          # build + install + launch (phase 0)
NOTES.md          # findings: key table, device quirks, bugs
```

---

## Phase 0: Toolchain + hello world  (~30 min)
Goal: prove build, install, launch works before writing real code.
1. Check `~/Android/Sdk` has `platforms/android-17` (else use the newest platform with `--min-api 17`) and build-tools. Check `java -version`.
2. `app/AndroidManifest.xml`: package `com.y1.music`, minSdk 17, one `MainActivity` that shows "Hello Y1" in big white text on black.
3. `build.sh`: `aapt package` → `javac` → `d8 --min-api 17` → `zip`/`aapt add` → `zipalign` → `apksigner` (debug keystore) → `adb install -r` → `adb shell am start`.
4. Add `.gitignore` entries: `build/`, `*.keystore`.

**TEST 0:** `./build.sh` shows "Hello Y1" on the device screen. Second run (rebuild) takes under 15 s.
**SAVE:** `phase0: build script + hello world`. Tag `v0-toolchain`.

---

## Phase 1: Input mapping  (~30 min)  [PRD build steps 1-2]
Goal: know exactly what the wheel and keys send.
1. `adb shell getevent -lt`: press each physical key, spin wheel both ways, press center. Record code, device, and Android keycode via `Generic.kl`/`Stock.kl`.
2. Add a debug screen to the hello app: prints every `onKeyDown` keycode + `onGenericMotionEvent`. Confirms what the *app* really receives (not just the kernel).
3. Optional quick look at stock app (rea) only for: how it reads art, how it handles the wheel. Skip if step 1-2 already answer it.
4. Table the results in `PRD.md` Controls section and `NOTES.md`.

**TEST 1:** Every control (wheel up/down, center, play/pause, next, prev, back, headphone key) shows a distinct line on screen. Table complete.
**SAVE:** `phase1: key map documented`.

---

## Phase 2: Risk spike (FLAC + cover)  (~1 h)  [PRD build step 3]
Goal: kill risks 1 and 2 before building UI around them. Throwaway code is fine, but keep it in `spike/`.
1. Push 5 test files to the device: 2 normal FLAC, 2 hi-res FLAC (24-bit / 96k+), 1 mp3, 1 m4a with cover.
2. Spike activity: play each with `MediaPlayer`. Log: starts in under 1 s? stutters? `onError` codes?
3. `MediaMetadataRetriever.getEmbeddedPicture()` on each. Log: cover bytes returned or null, and decode time.
4. Write verdicts into `NOTES.md`: FLAC OK / which files fail; covers OK for FLAC yes/no.

**TEST 2:** Clear yes/no on both risks, written down.
**Decision gate:** if FLAC covers fail, decide now: mp3/m4a only, or drop covers. If hi-res FLAC stutters, note which spec and keep skip-on-failure.
**SAVE:** `phase2: spike results`. Tag `v1-spike`.

---

## Phase 3: Library scan + cache  (~2 h)  [PRD build step 4, risk 3]
Goal: 3,312 songs indexed once, startup instant after.
1. Query `MediaStore.Audio.Media` (Android's scanner already read the tags): id, path, title, artist, album, track, duration. Don't re-read tags ourselves.
2. Group into `artist → album → songs` (in memory), sorted A-Z; songs by track number.
3. Cache to one file (`library.dat`, simple line-based text via `BufferedWriter`; no DB, no JSON lib). Load cache on startup; rebuild only on "Rescan".
4. Handle empty/untagged: artist "Unknown Artist", album "Unknown Album".
5. Make sure MediaStore actually contains all 3,312 files (check count; trigger scan with `MediaScannerConnection` if short).

**TEST 3:** Log count = 3,312 (or explain the diff). First scan time recorded. Cold start with cache loads in under 1 s. 90 artists present.
**SAVE:** `phase3: library scan and cache`. Tag `v2-library`.

---

## Phase 4: Browse screens  (~2 h)  [PRD "Screens"]
Goal: navigate with only the wheel + center + back. No playing yet.
1. One reusable list screen: a `ListView`, big white text on black, selected row highlighted high-contrast. Wheel moves selection, center selects, back goes up. Touch ignored.
2. Home menu: Artists, Albums, Shuffle All, Now Playing, Settings (the last three can be stubs).
3. Artists → albums of that artist → songs (track number + title).
4. Albums (all) → songs.
5. Fix any lag: no per-row image loading, view recycling on, no work in `getView`.

**TEST 4:** On device, reach any song from Home in under 5 wheel/center actions. Scrolling the 90-artist list feels instant. Back always works and returns to the same selected row.
**SAVE:** `phase4: browse screens`. Tag `v3-browse`.

---

## Phase 5: Playback core  (~2 h)  [PRD build step 5, part 1]
Goal: pick a song, it plays, the queue advances.
1. `PlayerService` (foreground service, so music survives screen off and app switching). Owns the `MediaPlayer`, the queue (list of song ids + index), and play/pause/next/prev.
2. Select a song → queue = rest of that album/artist list in order, start playing.
3. Next on completion. Previous: restart if more than 3 s in, else previous song.
4. Bad file: `onError` → toast "can't play X", log to `NOTES.md`-style log file on the SD card, skip to next.
5. Hardware keys: play/pause, next, prev. Audio focus + `ACTION_AUDIO_BECOMING_NOISY` (pause on headphone unplug). Wired headphone media button via `MediaButtonReceiver`.

**TEST 5:** Play an album start to finish with no taps on screen. Skipping works both ways. A corrupt/renamed file gets skipped with a message. Song start under 1 s. Music continues with screen off.
**SAVE:** `phase5: playback and queue`. Tag `v4-playback`.

---

## Phase 6: Now Playing + shuffle  (~2 h)  [PRD build step 5, part 2]
1. Now Playing screen: title, artist, album, battery %, shuffle indicator. Cover (decode small, e.g. 300 px, off the UI thread; cache the last one).
2. Wheel = volume here (`AudioManager`).
3. Shuffle: Shuffle All (home), and shuffle inside an artist/album (menu row at top of those lists). Shuffle = shuffled copy of the queue, indicator on.
4. Home → Now Playing opens it; starting a song opens it automatically.

**TEST 6:** Cover appears and changes per song without UI hitching. Volume changes with the wheel. Shuffle All plays a random order and doesn't repeat until done. Battery shows a sane number.
**SAVE:** `phase6: now playing and shuffle`. Tag `v5-nowplaying`.

---

## Phase 7: Resume + screen behavior  (~1.5 h)
1. Save song id, position, and queue (+ shuffle flag) to `SharedPreferences` on pause, song change, and service stop.
2. On start: restore to Now Playing, **paused**, until play is pressed.
3. Screen off after 15 s while playing; any wheel/button wakes it; music keeps going (partial wake lock in the service, not the screen lock).

**TEST 7:** Play, kill the app (`adb shell am force-stop`), relaunch: shows the same song paused, press play, resumes within a few seconds of the same position. Screen turns off at ~15 s and wakes on wheel. Reboot test later in phase 9.
**SAVE:** `phase7: resume and screen timeout`. Tag `v6-resume`.

---

## Phase 8: Settings  (~1 h)  [PRD build step 6]
1. Bluetooth → `Settings.ACTION_BLUETOOTH_SETTINGS`.
3. Rescan library (rebuild cache, show count when done).
4. Screen brightness (a few fixed steps via wheel).
5. Launch Android: start the stock home/settings; must be reachable even if we're the launcher (see phase 9).

**TEST 8:** Each setting works and persists across relaunch. Bluetooth pairing screen opens and is reachable with the wheel. Test a real BT headphone: audio plays and (note result) its buttons work. That answers risk 4.
**SAVE:** `phase8: settings`. Tag `v7-settings`.

---

## Phase 9: Launcher + boot  (~1 h)  [PRD build steps 7-8]
**Safety first.** Before changing anything:
- Confirm `adb root` still works and `adb devices` sees the device.
- Write the recovery commands into `NOTES.md` BEFORE setting the launcher: `adb shell pm enable <pkg>`, `adb shell pm clear com.y1.music`, `adb uninstall com.y1.music`.
1. Add `HOME` + `DEFAULT` intent filter to `MainActivity`. Set as default home. Keep the stock launcher installed (not disabled) so "Launch Android" works.
2. Reboot. Measure time to menu.
3. Crash-loop guard: catch uncaught exceptions at startup, write the stack to the log, and fall back to the stock launcher after 2 crashes in a row.
4. Disable unused system packages with `pm disable` in batches of ~5 from the list of ~40, rebooting and checking after each batch. Keep a script `disable.sh` and the exact reverse `enable.sh`. Never disable: `MtkBt`, the app itself, the stock launcher, Settings, anything audio/media-scanner related.

**TEST 9:** Cold boot lands on our home menu in under 20 s. Music resumes paused on Now Playing. Bluetooth still works. `enable.sh` restores everything (run it once to prove it).
**SAVE:** `phase9: launcher and disabled apps`. Tag `v8-launcher`.

---

## Phase 10: Hardening + v1 release  (~1.5 h)
1. Soak test: full day of use. Shuffle All for 2+ hours: no memory growth, no stutter, no crash.
2. Test the odd cases: unplug headphones, battery low, SD card missing/ejected at boot, songs with missing tags, Unicode names.
3. Re-check performance targets in the PRD (menus instant, song under 1 s, boot under 20 s). Log numbers in `NOTES.md`.
4. Update `CONTEXT.md` ("Done so far") and `PRD.md` (key table, risk outcomes). Short `README.md`: how to build, install, recover.

**TEST 10:** Everything in the PRD "Screens", "Behavior" and "Performance targets" ticked off on the device.
**SAVE:** `phase10: v1 hardening`. Tag `v1.0`. Push to GitHub (when you decide on a remote).

---

## Test points cheat sheet
| After | Checks |
|---|---|
| 0 | Build + install + launch works |
| 1 | All controls identified |
| 2 | FLAC plays, covers readable (or decision made) |
| 3 | 3,312 songs, fast cached startup |
| 4 | Wheel navigation, no lag |
| 5 | Album plays through, bad files skipped |
| 6 | Cover, volume, shuffle |
| 7 | Kill + relaunch resumes; screen timeout |
| 8 | Settings persist; BT headphones tested |
| 9 | Boot time, recovery scripts proven |
| 10 | PRD checklist complete |

## Where I'll stop and ask you
- Phase 2 decision gate (covers / FLAC fallback).
- Before Phase 9 step 1 (setting launcher) and step 4 (disabling system apps).
- Before any push to GitHub (no remote is set yet).

## Total estimate
About 15 hours of work, spread over ~6 short sessions. Phases 0-2 are the first session.

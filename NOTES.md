# NOTES
## Toolchain
- build.sh: aapt/javac(--release 8, android-36 jar)/d8 --min-api 17/zipalign/apksigner (keystore ~/.android/y1-debug.keystore). Rebuild+install ~5s.
- Screenshot: `adb shell screencap -p /data/local/tmp/s.png; adb pull` (exec-out fails; /sdcard read-only for shell). Screen may be asleep: `adb shell input keyevent 26` toggles.
- Screen is 480x360.
## Progress
- phase0 done.
## Key map (verified in app via onKeyDown, phase 1)
| Control | Kernel code (dev) | Android keycode |
|---|---|---|
| Wheel clockwise (first spin direction) | KEY_DOWN 108 (mtk-tpd) | DPAD_RIGHT (22) |
| Wheel counter-clockwise | KEY_UP 103 (mtk-tpd) | DPAD_LEFT (21) |
| Center | 232 (dev 1) | ENTER (66) |
| Play/pause | 164 | MEDIA_PLAY_PAUSE (85) |
| Next | 106 | MEDIA_NEXT (87) |
| Previous | 105 | MEDIA_PREVIOUS (88) |
| Back | 158 | BACK (4) |
| Wired headphone button | untested (ACCDET dev, 0072/0073/00a3-a6) | ? |
- One event per wheel click, no repeat. No MotionEvents from wheel.
- Assumption: clockwise = down the list. Confirm in phase 4.
- Debug screen swallows BACK; real app must handle it.
- Wheel presses were missing from the first logcat pass (cause unknown, worked on the second pass); watch for it.
- phase1 done.
## Phase 2 blockers (found)
- Spike files pushed to /storage/sdcard1/Spike (n1,n2 normal flac; h1,h2 96k/24bit flac; m1.mp3; a1.m4a). Spike code in spike/SpikeActivity.java (not committed yet).
- ALL playback + cover reads fail with EACCES: SD card is vfat mounted uid/gid 1023 (media_rw), fmask=0007. Normal apps (gids 1028 only) can't read it, even with READ_EXTERNAL_STORAGE.
- `com.android.providers.media` is NOT installed -> no MediaStore, no media scanner. PRD/plan phase 3 (MediaStore query) can't work. /storage/sdcard0 is an empty placeholder.
- Stock app com.innioasis.y1 runs as uid 1000 (sharedUserId android.uid.system), signed with the public AOSP test platform key (cert SHA1 27196e38...). Build is test-keys. That's how it reads the card and scans itself.
- adb shell is already root on this device.
- SD card df shows only ~433M total, and holds no music (only LOST.DIR + wallpapers). The 86 GB library is not on the device.
## Phase 2 results (resolved)
- Decision (user): app runs as system uid. AndroidManifest sharedUserId=android.uid.system; build.sh signs with the public AOSP platform test key from ~/.android/aosp (not in repo). Fixes SD access. Phase 3 must scan files itself (no MediaStore): walk the card, read tags with MediaMetadataRetriever, cache to library.dat.
- FLAC risk: PASS for 44.1k/48k, 16 and 24 bit (start 40-110 ms, position tracks wall clock over 8 s, no errors). FAIL for >48k (88.2k/96k/192k 24-bit): setDataSource status 0x80000000 -> skip-on-failure. Library has ~87 such files of 3,312 (37x88.2k, 25x96k, 25x192k). mp3 start ~40 ms, m4a ~130 ms. Audible glitches not checked by ear: listen in phase 5.
- Cover risk: PASS. FLAC covers readable (n1: 516 KB, 1280px, fetch 58 ms, decode 291 ms at sample 1/4; n2: 96 KB, fetch 7 ms). m4a cover 299 KB fetch ~120 ms. mp3 test file had no cover. Decode off the UI thread.
- Quirk: logcat sometimes drops the first line(s) of a run; and the device dropped off USB once mid-run (came back after replug).
- SD card: only ~433 MB total and no music on it (test files only). Real library must be copied before phase 3's 3,312 count test.
- phase2 done.
## Storage layout (corrected, phase 3)
- /storage/sdcard0 = the REAL 128 GB SD card (/dev/block/mmcblk1p1, 116G FAT32, ~27G used). Music at /storage/sdcard0/Music (32 artist folders, 1030 flac + 40 jpg so far; user is still copying the rest). Also has .rockbox, Android, backup... Never format/delete here.
- /storage/sdcard1 = INTERNAL flash partition (mmcblk0p7, 434 MB). Earlier I wrongly took it for the SD card, so the spike files in /storage/sdcard1/Spike are gone and I reformatted it with vold/newfs_msdos (wallpapers, LOST.DIR and my test files lost; approved by user thinking it was the SD card). Nothing of the user's music was there.
- GOTCHA: with USB plugged into the PC, sys.usb.config = mass_storage,adb and vold SHARES (unmounts) the card from Android. Before testing run: `adb shell setprop sys.usb.config adb; adb shell vdc volume unshare /storage/sdcard0 ums; adb shell vdc volume unshare /storage/sdcard1 ums; adb shell vdc volume mount /storage/sdcard0`. Resets on reboot/replug. The app can't read the card while shared.
## Phase 3 results
- Library.java: own scan (no MediaStore) with MediaMetadataRetriever; album-artist preferred (collabs stay together); cache = files/library.dat (tab lines, sorted). Stale cache must be deleted on path changes (Rescan comes in phase 8).
- Test: 1030 songs on card (library still being copied; 3,312 not testable yet). Scan 16 s (24 s with plain artist tag) -> est. ~50 s for 3,312. Cache load 130 ms for 1030 -> est. <0.5 s for 3,312. 38 artists / 76 albums for 32 folders (collab tags). 33 rows fall to Unknown Artist/Album. Unicode titles fine.
- TODO phase 10: re-run on the full 3,312 once copied.
- phase3 done.
## Phase 4 results
- MainActivity = one list screen (modes home/artists/albums/artist/songs via intent extras m/a/b). Library is a static singleton, loaded on a thread from Home. Wheel: DPAD_RIGHT=down, DPAD_LEFT=up, ENTER selects, BACK = activity finish (previous activity keeps its selected row).
- Verified on device via adb keyevents + screenshots: Home -> Albums -> songs, back restores the same selected row at each level; touch ignored; no crash.
- Songs: selecting shows a toast stub (phase 5). Home Shuffle All / Now Playing / Settings are toast stubs.
- Test notes: the "<5 actions" target holds for menu depth (Home -> list -> songs = 3 enters) but long lists need many wheel clicks (no letter jump by PRD). Hand-test the feel of the 38-artist list.
- Dev tip: `adb shell svc power stayon true` keeps the screen on while plugged; `input keyevent 26` toggles power (check dumpsys power | grep mWakefulness first).
- phase4 done.
## Phase 5 results
- PlayerService (foreground, static queue/idx/playing, action intents play/toggle/next/prev), MediaButtonReceiver (registerMediaButtonEventReceiver), noisy-receiver pause, audio focus (loss -> pause, gain -> resume), MediaPlayer.setWakeMode PARTIAL. Queue = the whole list shown, starting at the selected song.
- Verified on device: song starts in 40-110 ms; toggle/next/prev via adb keyevents; prev restarts only if >3 s in; auto-advance across 3 synthetic 3 s tracks into the 4th; bad files (missing, non-audio) -> toast "can't play X", line in /storage/sdcard0/y1-music.log, skip to next; playback continues with screen off (wake locks held).
- NOT verifiable via adb (hand-test): hardware media keys with the screen OFF (injected keyevents are dropped while asleep), unplugging headphones pauses, actually listening for stutter, hi-res (>48k) skip.
- phase5 done (hand-tests pending).
## Phase 6 results
- NowPlayingActivity: cover (240 px box, decoded at 1/2^n so >=300 px, off UI thread; embedded art first, else cover.jpg/folder.jpg in the song's folder), title/artist/album, Paused / SHUFFLE flags, battery %, volume n/max (device max is 13). Wheel: clockwise = louder. Polls PlayerService twice a second.
- Shuffle: PlayerService.play(..., shuffle=true) queues a shuffled copy of the list (each song once). Home "Shuffle All"; "Shuffle" first row in artist (albums) and album (songs) lists. Starting a song / shuffle opens Now Playing; Home "Now Playing" row opens it.
- Verified on device with screenshots: cover shows and changes on next; volume 13 -> 10 with 3 ccw clicks; SHUFFLE shown; battery 61%. Many tracks have no embedded art (the card's folders carry cover.jpg instead), hence the fallback.
- Hand-test: cover change smoothness while skipping fast.
- phase6 done.
## Phase 7 results
- Resume: queue -> files/queue.dat (library.dat line format, written when a queue starts), idx/pos/shuffle -> prefs "p" (written on pause, song start, service stop, and every 5 s while playing). MainActivity restores in a fresh process and opens Now Playing paused; play resumes via seekTo(pos).
- Verified: played track 3, force-stop at ~39.5 s, relaunch -> Now Playing shows the same song "Paused"; pressing play restarted at 39.57 s (saved position at the last 5 s tick).
- Screen: Base activity sets window brightness 0 after 15 s idle while playing -> driver clamps to 30/255 (backlight 128 -> 30, verified via /sys/class/leds/lcd-backlight/brightness). Any key restores 128; wheel/center that wake it are swallowed, media keys pass through.
- DECISION (user): no real screen-off. Wheel/buttons lack WAKE flags in Generic.kl (only center 232 has WAKE_DROPPED), so a sleeping screen would not wake on the wheel (that is also why wheel presses vanished in the first phase-1 pass). Generic.kl left untouched. Power button still sleeps/wakes the screen normally.
- Reboot test of resume is part of phase 9.
- phase7 done.
## Phase 8 results
- Settings (MainActivity mode "settings", Home row 5): Bluetooth (Settings.ACTION_BLUETOOTH_SETTINGS), Covers On/Off (prefs "covers"), Rescan library (thread, shows "(N songs)"), Brightness (ENTER cycles 12/24/39/59/78/100%, writes system setting), Launch Android.
- "Launch Android" starts com.android.settings: there is no separate stock launcher; the HOME activity is the Y1 player itself (com.innioasis.y1 SplashActivity). IMPORTANT for phase 9: once we are HOME, escape = Settings app, and the stock player can still be started with `am start -n com.innioasis.y1/.activity.SplashActivity`.
- Verified on device: covers toggle persists across relaunch (prefs), rescan -> 1030 songs, brightness values written (150/255 seen), BT settings screen opens and the wheel (LEFT/RIGHT) moves focus (toggle -> SEARCH FOR DEVICES), center toggles; Back returns to our screen; Settings app opens and Back returns.
- NOT verified (needs hands): pairing a real BT headphone, audio over BT, headphone buttons (risk 4).
- phase8 done (BT headphone hand-test pending).
## Phase 9 recovery (written BEFORE changing the launcher)
- adb root works (adb shell is uid 0 already). Device id 0123456789ABCDEF.
- Undo launcher: `adb uninstall com.y1.music` (stock com.innioasis.y1 is HOME again). Or `adb shell pm clear com.y1.music`.
- Our HOME filter has priority 1 (stock = 0), so there is no chooser and no `set default` step; nothing in /system is touched.
- Start stock player by hand: `adb shell am start -n com.innioasis.y1/.activity.SplashActivity`. Settings: `am start -n com.android.settings/.Settings`.
- If a disabled package breaks boot: `adb shell pm enable <pkg>` or run disable/enable script `enable.sh`. If adb is gone, `adb wait-for-device` after a reboot (adbd starts early).
- Crash guard: 2 uncaught crashes in a row (count in prefs "crashes", reset after 10 s of home life) -> home hands over to the stock player; stack goes to /storage/sdcard0/y1-music.log.
## Phase 9 results
- Launcher: priority="1" on the HOME filter is ignored (non-/system apps get priority reset to 0), so the HOME chooser appeared; picked "Y1 Music" + Always once (tap). Preferred HOME survives reboot. Undo: `adb uninstall com.y1.music` (or Settings > Apps > clear defaults).
- Boot: kernel start -> our Home process 8 s, menu/NowPlaying displayed ~9.5 s (target <20 s). Boot lands on Now Playing, paused (resume works after reboot). Stock player's BOOT_COMPLETED receiver does not steal focus.
- Crash guard verified: prefs crashes=2 -> Home starts stock player (com.innioasis.y1 MainActivity), counter reset to 0, log line "crash guard". HOME press afterwards returns to ours.
- disable.sh / enable.sh (repo root): 20 packages in 4 batches (live wallpapers, dreams, protips, exchange, calendar, datatransfer, apst, lbs.em, stk, pico TTS, rukeyboard). Rebooted after each batch: always landed on our app, MtkBt (com.mediatek.bluetooth) + mtkbt still running. enable.sh run once: 0 disabled left; disable.sh re-applied (20 disabled now). Never touched: MtkBt, systemui, settings, providers.*, inputmethod.latin, both players.
- With USB plugged to the PC, UsbStorageActivity (systemui) covers our app on boot until the unshare commands in "Storage layout" are run: PC-only quirk.
- NOT verified (hands): BT headphone pairing/audio still working after boot (BT daemons are up), power-button cold boot (adb reboot used).
- phase9 done.
## Wheel in Android Settings (car test)
- Wheel was DPAD_LEFT/RIGHT system-wide, which can't move focus down a vertical list in Settings/BT. Generic.kl on device now maps 103->DPAD_UP, 108->DPAD_DOWN (original saved as backups/Generic.kl.y1-stock). App accepts both; NowPlaying volume too. Verified with raw sendevent on event2 after reboot: 3 clicks -> Bluetooth row focused.
- Not reproduced: Back/center dead. Suspect UsbStorageActivity covering the screen when plugged into the car USB (same PC quirk). Hand-test in car.
- Fix for "Android screen, wheel dead after unplug": it was systemui's UsbStorageActivity (touch-only) staying on top. UsbReceiver (UMS_DISCONNECTED / USB_STATE connected=false) brings our task to front. Tested by simulated broadcast; real unplug not tested.
## Screen-off + USB row
- SUPERSEDES the "no real screen-off" decision: while playing, Base dims at 15 s idle then calls PowerManager.goToSleep 15 s later. Music survives (PARTIAL wake locks held, checked via dumpsys power). Hand-verified: left/right change songs and play/pause work with the screen off; center (WAKE_DROPPED) wakes it, that first press is swallowed. Generic.kl untouched by this.
- USB: stock UsbStorageActivity confirm dialog (Cancel/OK side by side) needs DPAD_RIGHT; wheel is UP/DOWN and left/right buttons are MEDIA_PREV/NEXT, so OK was unreachable. Added Settings row "USB storage: On/Off" (StorageManager enable/disableUsbMassStorage via reflection, app is uid system). Remapping 105/106 to DPAD was rejected: it would kill prev/next with the screen off.
- Row not hand-tested yet. Flow: plug in, Back out of the USB screen, Settings > USB storage. If it stays Off, grep logcat for "usb failed".

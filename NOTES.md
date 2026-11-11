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

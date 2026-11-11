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

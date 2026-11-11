# NOTES
## Toolchain
- build.sh: aapt/javac(--release 8, android-36 jar)/d8 --min-api 17/zipalign/apksigner (keystore ~/.android/y1-debug.keystore). Rebuild+install ~5s.
- Screenshot: `adb shell screencap -p /data/local/tmp/s.png; adb pull` (exec-out fails; /sdcard read-only for shell). Screen may be asleep: `adb shell input keyevent 26` toggles.
- Screen is 480x360.
## Progress
- phase0 done.

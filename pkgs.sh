#!/bin/bash
# Usage: ./pkgs.sh disable|enable [batch 1-4]  (no batch = all). disable turns off unused system apps; enable undoes it.
B1="com.android.galaxy4 com.android.magicsmoke com.android.musicvis com.android.noisefield com.android.phasebeam"
B2="com.android.wallpaper.holospiral com.android.wallpaper.livepicker com.android.dreams.basic com.android.dreams.phototable com.android.protips"
B3="com.android.exchange com.android.providers.calendar com.mediatek.calendarimporter com.mediatek.appwidget.worldclock com.mediatek.datatransfer"
B4="com.mediatek.apst.target com.mediatek.lbs.em com.android.stk com.svox.pico org.liskovsoft.androidtv.rukeyboard"
# never: com.mediatek.bluetooth (MtkBt), com.y1.music, com.innioasis.y1, com.android.settings, systemui, providers.*
[ "$1" = enable ] || [ "$1" = disable ] || { echo "usage: $0 disable|enable [1-4]"; exit 1; }
if [ -n "$2" ]; then eval L=\$B$2; else L="$B1 $B2 $B3 $B4"; fi
for p in $L; do adb shell pm $1 $p; done

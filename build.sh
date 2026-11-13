#!/bin/bash
# build + install + launch. Usage: ./build.sh
set -e
cd "$(dirname "$0")"
SDK=~/Android/Sdk; BT=$SDK/build-tools/35.0.0; JAR=$SDK/platforms/android-36/android.jar
# signed with the public AOSP platform test key (matches device, lets us run as uid system); keys live in ~/.android/aosp
rm -rf build; mkdir -p build/classes build/gen
$BT/aapt package -f -m -J build/gen -M app/AndroidManifest.xml -S app/res -I $JAR -F build/base.apk
javac --release 8 -Xlint:-options -cp $JAR -d build/classes $(find app/src build/gen -name '*.java')
$BT/d8 --min-api 17 --lib $JAR --output build $(find build/classes -name '*.class')
cp build/base.apk build/unsigned.apk
(cd build && zip -q unsigned.apk classes.dex)
$BT/zipalign -f 4 build/unsigned.apk build/aligned.apk
$BT/apksigner sign --key ~/.android/aosp/platform.pk8 --cert ~/.android/aosp/platform.x509.pem --out build/y1.apk build/aligned.apk
adb install -r build/y1.apk
adb shell am start -n com.y1.music/.MainActivity

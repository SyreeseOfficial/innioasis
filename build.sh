#!/bin/bash
# build + install + launch. Usage: ./build.sh
set -e
cd "$(dirname "$0")"
SDK=~/Android/Sdk; BT=$SDK/build-tools/35.0.0; JAR=$SDK/platforms/android-36/android.jar
KS=~/.android/y1-debug.keystore
[ -f $KS ] || keytool -genkeypair -keystore $KS -storepass android -keypass android -alias y1 -dname CN=y1 -keyalg RSA -keysize 2048 -validity 10000
rm -rf build; mkdir -p build/classes build/gen
$BT/aapt package -f -m -J build/gen -M app/AndroidManifest.xml -S app/res -I $JAR -F build/base.apk
javac --release 8 -Xlint:-options -cp $JAR -d build/classes $(find app/src build/gen -name '*.java')
$BT/d8 --min-api 17 --lib $JAR --output build $(find build/classes -name '*.class')
cp build/base.apk build/unsigned.apk
(cd build && zip -q unsigned.apk classes.dex)
$BT/zipalign -f 4 build/unsigned.apk build/aligned.apk
$BT/apksigner sign --ks $KS --ks-pass pass:android --out build/y1.apk build/aligned.apk
adb install -r build/y1.apk
adb shell am start -n com.y1.music/.MainActivity

#!/usr/bin/env bash
# Сборка APK без Gradle: aapt2 → javac → d8 → apksigner.
# Работает на ARM (Raspberry Pi, Orange Pi и т.п.), где официальный Android SDK не запускается.
#
# Что нужно заранее:
#   1) aapt2 для ARM — в Debian это пакет android-sdk-build-tools (даёт /usr/bin/aapt2)
#   2) android.jar (платформа) и d8.jar / apksigner.jar (build-tools) — скачиваются с dl.google.com:
#        curl -LO https://dl.google.com/android/repository/platform-34-ext7_r03.zip
#        curl -LO https://dl.google.com/android/repository/build-tools_r34-linux.zip
#        unzip platform-34-ext7_r03.zip -d "$ANDROID_SDK"
#        unzip build-tools_r34-linux.zip   -d "$ANDROID_SDK"
#      (распакуется как android-34/ и android-14/ — имена архивов такие исторически)
#   3) JDK 17+: javac, keytool
#
# Запуск:
#   ANDROID_SDK="$HOME/android-sdk" ./scripts/build-apk.sh
set -euo pipefail

ANDROID_SDK="${ANDROID_SDK:-$HOME/android-sdk}"
HERE="$(cd "$(dirname "$0")" && pwd)"
PROJECT="${PROJECT:-$(cd "$HERE/.." && pwd)/app}"
OUT="${OUT:-$(cd "$HERE/.." && pwd)/build}"
KS="${KS:-$HOME/.android-weather-widget/keystore.jks}"
KS_PASS="${KS_PASS:-android}"
APP_NAME="${APP_NAME:-weather-widget}"

AJAR="$ANDROID_SDK/android-34/android.jar"
BT="$ANDROID_SDK/android-14/lib"
D8="$BT/d8.jar"
SIGNER="$BT/apksigner.jar"

for f in "$AJAR" "$D8" "$SIGNER"; do
    if [ ! -f "$f" ]; then
        echo "Не найден $f" >&2
        echo "Скачайте платформу и build-tools (см. комментарий в начале скрипта)." >&2
        exit 1
    fi
done
command -v aapt2 >/dev/null || { echo "Нет aapt2. В Debian: sudo apt install android-sdk-build-tools" >&2; exit 1; }
command -v javac >/dev/null || { echo "Нет javac. Поставьте JDK 17+: sudo apt install openjdk-17-jdk-headless" >&2; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"
cd "$PROJECT"

echo "1/6 ресурсы → flat"
aapt2 compile --dir res -o "$OUT/res.zip"

echo "2/6 линковка (манифест + ресурсы + R.java)"
aapt2 link -o "$OUT/base.apk" \
    -I "$AJAR" \
    --manifest AndroidManifest.xml \
    -R "$OUT/res.zip" \
    --java "$OUT/gen" \
    --auto-add-overlay

echo "3/6 компиляция java"
javac -source 8 -target 8 -nowarn \
    -bootclasspath "$AJAR" -cp "$AJAR" \
    -d "$OUT/classes" \
    $(find src "$OUT/gen" -name "*.java")

echo "4/6 dex (d8)"
java -cp "$D8" com.android.tools.r8.D8 \
    --lib "$AJAR" --min-api 23 \
    --output "$OUT/dex" \
    $(find "$OUT/classes" -name "*.class")

echo "5/6 упаковка classes.dex в apk"
cp "$OUT/base.apk" "$OUT/app-unsigned.apk"
(cd "$OUT/dex" && zip -q -X "$OUT/app-unsigned.apk" classes.dex)

echo "6/6 подпись"
if [ ! -f "$KS" ]; then
    echo "   создаю ключ подписи (пароль '$KS_PASS', поменяйте для реального проекта)"
    keytool -genkeypair -keystore "$KS" -alias app -keyalg RSA -keysize 2048 \
        -validity 10950 -storepass "$KS_PASS" -keypass "$KS_PASS" \
        -dname "CN=$APP_NAME, O=Home, C=RU" >/dev/null 2>&1
fi
java -jar "$SIGNER" sign \
    --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --out "$OUT/$APP_NAME.apk" "$OUT/app-unsigned.apk"

java -jar "$SIGNER" verify "$OUT/$APP_NAME.apk" >/dev/null && echo "   подпись проверена"
ls -la "$OUT/$APP_NAME.apk"
echo "готово: $OUT/$APP_NAME.apk"

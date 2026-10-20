#!/usr/bin/env bash
set -euo pipefail
app_id='com.stevenris.reader'
adb logcat -c

# Check both Android's package installer and a debug -> release update.
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -W -n "$app_id/.MainActivity"
for attempt in $(seq 1 10); do
    if adb shell pidof "$app_id" > /dev/null; then
        break
    fi
    sleep 1
done
sleep 3
adb shell pidof "$app_id"
adb shell dumpsys package "$app_id" > app/build/reports/installed-package.txt
if grep -E 'pkgFlags=.*DEBUGGABLE' app/build/reports/installed-package.txt; then
    echo "Release APK must not be debuggable"
    exit 1
fi

# Read the UI hierarchy to confirm that the library screen actually renders.
adb shell uiautomator dump /sdcard/reader-ui.xml
adb pull /sdcard/reader-ui.xml app/build/reports/reader-ui.xml
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
p = Path("app/build/reports/reader-ui.xml")
root = ET.parse(p).getroot()
texts = [node.attrib.get("text", "") for node in root.iter()]
assert any("Тихий ридер" in text for text in texts), texts
assert any("Скачать книгу" in text for text in texts), texts
print("Android 16 install, debug-to-release upgrade and library screen: OK")
PY
adb logcat -d -b crash > app/build/reports/crash-log.txt
if grep -q 'FATAL EXCEPTION' app/build/reports/crash-log.txt; then
    cat app/build/reports/crash-log.txt
    exit 1
fi

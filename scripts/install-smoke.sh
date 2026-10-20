#!/usr/bin/env bash
set -euo pipefail
app_id='com.stevenris.reader'
adb logcat -c
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Seed both the v1 plain-text storage and the v2 heading-aware storage before
# upgrading the app, so CI exercises preservation of an existing library.
python3 - <<'PY'
import json, shlex, subprocess
app = "com.stevenris.reader"
def command(args, data=None):
    return subprocess.run(["adb", "shell", shlex.join(["run-as", app] + args)],
                          input=data, check=True, capture_output=True)
def write(path, content):
    command(["sh", "-c", "cat > " + shlex.quote(path)], content.encode("utf-8"))
for book in (9001, 9002):
    command(["mkdir", "-p", "files/books/" + str(book)])
command(["mkdir", "-p", "shared_prefs"])
write("files/books/9001/meta.json", json.dumps({"id":9001,"title":"Старая тестовая книга","total":1}))
write("files/books/9001/1.txt", "Сохранённый текст из предыдущей версии. Его нельзя потерять при обновлении приложения.")
title = "Тестовый раздел"
text = title + "\n\nУчебный абзац, созданный для проверки заголовков и перехода к главе."
write("files/books/9002/meta.json", json.dumps({"id":9002,"title":"Новая тестовая книга","total":1}))
write("files/books/9002/1.json", json.dumps({"format":2,"text":text,
      "headings":[{"title":title,"offset":0,"level":2}]}))
write("shared_prefs/reader.xml",
      '<?xml version="1.0" encoding="utf-8"?><map>'
      '<int name="lastBook" value="9002"/><int name="page_9002" value="1"/>'
      '<int name="font" value="21"/></map>')
PY

adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -W -n "$app_id/.MainActivity"
sleep 3
adb shell pidof "$app_id"
adb shell dumpsys package "$app_id" > app/build/reports/installed-package.txt
if grep -E 'pkgFlags=.*DEBUGGABLE' app/build/reports/installed-package.txt; then
    echo "Release APK must not be debuggable"
    exit 1
fi

python3 - <<'PY'
import re, subprocess, time, xml.etree.ElementTree as ET
def run(*args):
    return subprocess.run(args, check=True, capture_output=True, text=True, timeout=30)
def screen(name):
    run("adb", "shell", "uiautomator", "dump", "/sdcard/reader-ui.xml")
    target = "app/build/reports/" + name + ".xml"
    run("adb", "pull", "/sdcard/reader-ui.xml", target)
    return list(ET.parse(target).getroot().iter())
def texts(nodes):
    return [n.attrib.get("text", "") for n in nodes]
def tap(node):
    x1,y1,x2,y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
    run("adb", "shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(1)
nodes = screen("reader-restored")
assert "Главы" in texts(nodes), texts(nodes)
assert any("Тестовый раздел" in t for t in texts(nodes)), texts(nodes)
tap(next(n for n in nodes if n.attrib.get("text") == "Главы"))
nodes = screen("reader-chapters")
assert any("Тестовый раздел" in t and "стр. 1" in t for t in texts(nodes)), texts(nodes)
tap(next(n for n in nodes if "стр. 1" in n.attrib.get("text","")))
nodes = screen("reader-chapter-navigation")
assert any("Тестовый раздел" in t for t in texts(nodes)), texts(nodes)
run("adb", "shell", "input", "keyevent", "4")
time.sleep(1)
nodes = screen("reader-legacy-library")
assert "Обновить текст" in texts(nodes), texts(nodes)
assert any("Старая тестовая книга" in t for t in texts(nodes)), texts(nodes)
# The old-format book must still open after the update.
read_buttons = [n for n in nodes if n.attrib.get("text") == "Читать"]
assert len(read_buttons) == 2, texts(nodes)
# Sorted titles: Новая before Старая, so the second entry is the old book.
tap(read_buttons[1])
nodes = screen("reader-legacy-text")
assert any("Сохранённый текст" in t for t in texts(nodes)), texts(nodes)
print("Android 16 install, upgrade, legacy library, chapter list and navigation: OK")
PY
adb logcat -d -b crash > app/build/reports/crash-log.txt
if grep -q 'FATAL EXCEPTION' app/build/reports/crash-log.txt; then
    cat app/build/reports/crash-log.txt
    exit 1
fi

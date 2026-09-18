#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET

path = Path("app/src/main/res/drawable/ic_stat_gbw.xml")
text = path.read_text(encoding="utf-8")
root = ET.fromstring(text)
android = "{http://schemas.android.com/apk/res/android}"
if root.attrib.get(android + "width") != "24dp" or root.attrib.get(android + "height") != "24dp":
    raise SystemExit("Notification small icon must be exactly 24dp")
if "M4,4h16v16h-16z" in text or "M0,0h24v24" in text:
    raise SystemExit("Notification small icon still contains an opaque rectangular background")
paths = list(root)
if not paths:
    raise SystemExit("Notification small icon has no vector paths")
has_transparent = any(p.attrib.get(android + "fillColor") == "@android:color/transparent" for p in paths)
has_visible = any(p.attrib.get(android + "fillColor") == "#FFFFFFFF" or
                  p.attrib.get(android + "strokeColor") == "#FFFFFFFF" for p in paths)
if not has_transparent or not has_visible:
    raise SystemExit("Notification small icon must use a transparent background and visible monochrome glyph")
print("Notification small icon mask: PASS")

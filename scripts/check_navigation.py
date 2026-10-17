"""Fail CI when a navigation destination references a missing Java Fragment."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
errors = []
count = 0
for graph in (ROOT / "app/src/main/res/navigation").glob("*.xml"):
    for node in ET.parse(graph).iter("fragment"):
        name = node.get(ANDROID + "name", "")
        source = ROOT / "app/src/main/java" / (name.replace(".", "/") + ".java")
        count += 1
        if not source.is_file():
            errors.append(f"{graph.name}: missing destination {name}")
if errors:
    sys.exit("\n".join(errors))
print(f"Navigation contract: {count} Fragment destinations exist.")

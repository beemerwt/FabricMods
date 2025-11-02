#!/usr/bin/env python3
"""
Extracts the default Minecraft sounds.json from the asset index without any CLI args.

Configuration:
  - Set ASSETS_DIR to the root that contains the 'indexes' and 'objects' subfolders.

Behavior:
  - Picks the highest version index JSON found in ASSETS_DIR/indexes (e.g., 1.21.10.json).
  - Looks up the entry whose key ends with 'sounds.json'.
  - Copies the corresponding hashed object to ./sounds.json.

Notes:
  - This script does NOT decompress; it just copies bytes.
  - It prints clear messages for all common failure modes.
"""

import json
import os
import re
import shutil
from pathlib import Path

# >>>>>>>>>>>>>>>>>>>>>  EDIT THIS  <<<<<<<<<<<<<<<<<<<<<<
ASSETS_DIR = "C:\\Users\\Beemer\\AppData\\Roaming\\ModrinthApp\\meta\\assets"
# <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

def _parse_version_from_filename(name: str):
    """
    Returns a tuple of ints for version sorting.
    Accepts filenames like '1.21.10.json', '1.21.json', '1.21.10-pre1.json'.
    Non-numeric suffixes are ignored for numeric comparison.
    """
    # Pull leading numeric dotted version (e.g., 1.21.10 from 1.21.10-pre1.json)
    m = re.match(r"(\d+(?:\.\d+){0,3})", name)
    if not m:
        return ()
    parts = [int(p) for p in m.group(1).split(".")]
    # Normalize length so 1.21 < 1.21.1 when comparing tuples
    while len(parts) < 4:
        parts.append(0)
    return tuple(parts)

def _pick_highest_index(indexes_dir: Path) -> Path | None:
    if not indexes_dir.is_dir():
        return None
    candidates = []
    for p in indexes_dir.glob("*.json"):
        ver = _parse_version_from_filename(p.stem)
        # Use file mtime as tie-breaker in case versions parse equally
        mtime = p.stat().st_mtime
        candidates.append((ver, mtime, p))
    if not candidates:
        return None
    # Sort by (version tuple asc, mtime asc) and take the last (highest/newest)
    candidates.sort()
    return candidates[-1][2]

def main():
    assets_path = Path(ASSETS_DIR)
    indexes_dir = assets_path / "indexes"
    objects_dir = assets_path / "objects"

    if not indexes_dir.is_dir():
        print(f"❌ Missing indexes directory: {indexes_dir}")
        return
    if not objects_dir.is_dir():
        print(f"❌ Missing objects directory: {objects_dir}")
        return

    index_file = _pick_highest_index(indexes_dir)
    if not index_file:
        print(f"❌ No index files found in {indexes_dir}")
        return

    print(f"ℹ️ Using index: {index_file.name}")

    try:
        with index_file.open("r", encoding="utf-8") as f:
            index = json.load(f)
    except Exception as e:
        print(f"❌ Failed to read index JSON: {e}")
        return

    objects = index.get("objects", {})
    if not isinstance(objects, dict):
        print("❌ Index missing 'objects' map.")
        return

    # Find the entry for sounds.json (case-insensitive)
    target_key = next((k for k in objects.keys() if k.lower().endswith("sounds.json")), None)
    if not target_key:
        print("❌ Could not find a key ending with 'sounds.json' in the index.")
        return

    entry = objects.get(target_key) or {}
    hash_value = entry.get("hash")
    if not hash_value:
        print(f"❌ Entry for {target_key} has no 'hash' field.")
        return

    src = objects_dir / hash_value[:2] / hash_value
    if not src.is_file():
        print(f"❌ Object file not found: {src}")
        return

    dest = Path.cwd() / "sounds.json"
    try:
        shutil.copyfile(src, dest)
    except Exception as e:
        print(f"❌ Failed to copy object to {dest}: {e}")
        return

    print(f"✅ Copied {target_key} (hash {hash_value}) → {dest}")

if __name__ == "__main__":
    main()
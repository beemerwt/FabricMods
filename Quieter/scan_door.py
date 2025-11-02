#!/usr/bin/env python3
"""
Scan a JSON file (or stdin) and recursively find any keys or values containing "door" (case-insensitive).
Usage: python find_doors.py path/to/file.json
    cat file.json | python find_doors.py
"""

import sys
import json
from typing import Any, List

TERM = "door"

def format_path(path: List[Any]) -> str:
    out = "$"
    for p in path:
     if isinstance(p, int):
         out += f"[{p}]"
     else:
         # quote keys that contain non-identifier chars
         if str(p).isidentifier():
          out += f".{p}"
         else:
          out += f'["{p}"]'
    return out

def find_matches(obj: Any, path: List[Any] = None):
    if path is None:
        path = []
    matches = []
    if isinstance(obj, dict):
        for k, v in obj.items():
            # Only search keys for TERM; record the containing object for output
            if TERM in str(k).lower():
                matches.append(("key", format_path(path + [k]), k, obj))
            # Recurse into the value regardless of its type
            matches.extend(find_matches(v, path + [k]))
    elif isinstance(obj, list):
        for i, item in enumerate(obj):
            matches.extend(find_matches(item, path + [i]))
    # primitives contain no keys, so nothing to do
    return matches

def main():
    if len(sys.argv) > 1:
     fname = sys.argv[1]
     try:
         with open(fname, "r", encoding="utf-8") as f:
          data = json.load(f)
     except Exception as e:
         print(f"Error reading JSON from {fname}: {e}", file=sys.stderr)
         sys.exit(2)
    else:
     try:
         data = json.load(sys.stdin)
     except Exception as e:
         print(f"Error reading JSON from stdin: {e}", file=sys.stderr)
         sys.exit(2)

    matches = find_matches(data)
    if not matches:
     print("No matches for 'door' found.")
     return

    for typ, path, key_or_index, value in matches:
     if typ == "key":
         print(f"[KEY]   {path}  -> key: {key_or_index!r}  value: {value!r}")
     else:
         print(f"[VALUE] {path}  -> value: {value!r}")

if __name__ == "__main__":
    main()
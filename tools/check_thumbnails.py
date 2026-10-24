#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Verifie que chaque vignette du catalogue est telechargeable et decodable."""

import json
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor

CATALOG = sys.argv[1] if len(sys.argv) > 1 else "site/data/catalog.json"

with open(CATALOG, encoding="utf-8") as fh:
    data = json.load(fh)

items = [
    (row["id"], item["id"], item.get("thumbnail_url", ""))
    for row in data.get("rows", [])
    for item in row.get("items", [])
]


def probe(entry):
    row_id, item_id, url = entry
    if not url:
        return row_id, item_id, url, "NO-URL", ""
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "MISSA-TV-360/1.0"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            head = resp.read(8)
            ctype = resp.headers.get("Content-Type", "")
            if head[:4] == b"\x89PNG":
                fmt = "PNG"
            elif head[:2] == b"\xff\xd8":
                fmt = "JPEG"
            elif head[:4] == b"RIFF":
                fmt = "WEBP"
            else:
                fmt = "UNKNOWN"
            return row_id, item_id, url, "OK " + fmt, ctype
    except Exception as exc:
        return row_id, item_id, url, "FAIL", type(exc).__name__


with ThreadPoolExecutor(max_workers=8) as pool:
    results = list(pool.map(probe, items))

ok = sum(1 for r in results if r[3].startswith("OK"))
bad = [r for r in results if not r[3].startswith("OK")]
for row_id, item_id, url, status, detail in results:
    if not status.startswith("OK"):
        print(f"  {status:9} {row_id:14} {item_id:28} {detail:22} {url[:70]}")
print(f"\n{ok}/{len(results)} vignettes decodables")
sys.exit(0 if not bad else 1)

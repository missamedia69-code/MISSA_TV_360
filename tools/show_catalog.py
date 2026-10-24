#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Affiche le catalogue consolidé (id de ligne, titre, vignette)."""

import json
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "site/data/catalog.json"
with open(path, encoding="utf-8") as handle:
    data = json.load(handle)

for row in data["rows"]:
    for item in row.get("items", []):
        print(
            "{:14} | {:36} | {}".format(
                row["id"][:14],
                item.get("title", "")[:36],
                item.get("thumbnail_url", "")[:80],
            )
        )

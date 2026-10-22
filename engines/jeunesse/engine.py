#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Engine « jeunesse » — Jeunesse. Éditez CONTENU pour gérer vos sources."""

from __future__ import annotations
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "_core"))
from schema import Item, placeholder_thumbnail, Row, run_engine, Stream  # noqa: E402

CONTENU = [('kids-bbb', 'Big Buck Bunny', 'Animation douce, Blender CC BY (droits vérifiés).', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4', 'mp4', False)]


def build_rows():
    items = []
    for (iid, title, desc, url, fmt, live) in CONTENU:
        items.append(Item(
            id=iid, title=title, kind='kids', description=desc,
            stream=Stream(url=url, format=fmt, live=live),
            audio_only=(fmt == "mp3"),
            thumbnail_url=placeholder_thumbnail(title, "kids"),
            source="Blender Foundation",
            license="CC BY 3.0",
            demo=False,
            rights_checked=True,
        ))
    return [Row(id='jeunesse', title='Jeunesse', items=items)]


if __name__ == "__main__":
    run_engine('jeunesse', build_rows)

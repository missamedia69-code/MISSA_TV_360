#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Engine « jeunesse » — Jeunesse. Éditez CONTENU pour gérer vos sources."""

from __future__ import annotations
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "_core"))
from schema import Item, Row, Stream, run_engine  # noqa: E402

CONTENU = [('kids-bbb', 'Big Buck Bunny', 'Animation douce, Blender CC BY.', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4', 'mp4', False)]


def build_rows():
    items = []
    for (iid, title, desc, url, fmt, live) in CONTENU:
        items.append(Item(
            id=iid, title=title, kind='kids', description=desc,
            stream=Stream(url=url, format=fmt, live=live),
            audio_only=(fmt == "mp3"),
            thumbnail_url=f"https://placehold.co/320x180/1a237e/ffd600?text={title.replace(" ", "+")}",
        ))
    return [Row(id='jeunesse', title='Jeunesse', items=items)]


if __name__ == "__main__":
    run_engine('jeunesse', build_rows)

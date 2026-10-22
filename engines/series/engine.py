#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Engine « series » — Séries. Éditez CONTENU pour gérer vos sources."""

from __future__ import annotations
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "_core"))
from schema import Item, placeholder_thumbnail, Row, run_engine, Stream  # noqa: E402

CONTENU = [('ser-a-e1', 'Les Grandes Escapades — É1', 'Épisode 1 (démonstration — contenu de test).', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4', 'mp4', False), ('ser-a-e2', 'Les Grandes Escapades — É2', 'Épisode 2 (démonstration — contenu de test).', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4', 'mp4', False)]


def build_rows():
    items = []
    for (iid, title, desc, url, fmt, live) in CONTENU:
        items.append(Item(
            id=iid, title=title, kind='series', description=desc,
            stream=Stream(url=url, format=fmt, live=live),
            audio_only=(fmt == "mp3"),
            thumbnail_url=placeholder_thumbnail(title, "series"),
            source="Démonstration",
            license="Libre accès (test)",
            demo=True,
            rights_checked=False,
        ))
    return [Row(id='series', title='Séries', items=items)]


if __name__ == "__main__":
    run_engine('series', build_rows)

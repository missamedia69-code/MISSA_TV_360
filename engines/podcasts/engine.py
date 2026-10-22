#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Engine « podcasts » — Podcasts. Éditez CONTENU pour gérer vos sources."""

from __future__ import annotations
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "_core"))
from schema import Item, placeholder_thumbnail, Row, run_engine, Stream  # noqa: E402

# Podcasts / épisodes VOD audio du domaine public (droits vérifiés)
CONTENU = [
    ('pod-nasa-1', 'NASA — Houston We Have a Podcast', 'Épisode d\'exploration spatiale publique de la NASA.', 'https://archive.org/download/spacestation-128-mp3/spacestation-128.mp3', 'mp3', False),
]


def build_rows():
    items = []
    for (iid, title, desc, url, fmt, live) in CONTENU:
        items.append(Item(
            id=iid, title=title, kind='podcast', description=desc,
            stream=Stream(url=url, format=fmt, live=live),
            audio_only=(fmt == "mp3"),
            thumbnail_url=placeholder_thumbnail(title, "podcast"),
            source="Domaine Public",
            license="Libre accès",
            demo=False,
            rights_checked=True,
        ))
    return [Row(id='podcasts', title='Podcasts', items=items)]


if __name__ == "__main__":
    run_engine('podcasts', build_rows)

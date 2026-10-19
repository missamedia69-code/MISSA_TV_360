#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Engine « musique » — Musique. Éditez CONTENU pour gérer vos sources."""

from __future__ import annotations
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "_core"))
from schema import Item, Row, Stream, run_engine  # noqa: E402

CONTENU = [('music-demo-1', 'Musique · Emplacement 1', 'Emplacement réservé musique.', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WhatCarCanYouGetForAGrand.mp4', 'mp4', False)]


def build_rows():
    items = []
    for (iid, title, desc, url, fmt, live) in CONTENU:
        items.append(Item(
            id=iid, title=title, kind='music', description=desc,
            stream=Stream(url=url, format=fmt, live=live),
            audio_only=(fmt == "mp3"),
            thumbnail_url=f"https://placehold.co/320x180/1a237e/ffd600?text={title.replace(" ", "+")}",
        ))
    return [Row(id='musique', title='Musique', items=items)]


if __name__ == "__main__":
    run_engine('musique', build_rows)

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Engine « films_courts » — Films courts. Éditez CONTENU pour gérer vos sources."""

from __future__ import annotations
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "_core"))
from schema import Item, Row, Stream, run_engine  # noqa: E402

CONTENU = [('short-bbb', 'Big Buck Bunny', 'Court métrage Blender (2008), CC BY.', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4', 'mp4', False), ('short-sintel', 'Sintel', 'Court métrage Blender (2010), CC BY.', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4', 'mp4', False)]


def build_rows():
    items = []
    for (iid, title, desc, url, fmt, live) in CONTENU:
        items.append(Item(
            id=iid, title=title, kind='short', description=desc,
            stream=Stream(url=url, format=fmt, live=live),
            audio_only=(fmt == "mp3"),
            thumbnail_url=f"https://placehold.co/320x180/1a237e/ffd600?text={title.replace(" ", "+")}",
        ))
    return [Row(id='films_courts', title='Films courts', items=items)]


if __name__ == "__main__":
    run_engine('films_courts', build_rows)

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Engine « films » — Films. Éditez CONTENU pour gérer vos sources."""

from __future__ import annotations
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "_core"))
from schema import Item, Row, Stream, run_engine  # noqa: E402

CONTENU = [('film-phantom', 'Phantom of the Opera', 'Film muet fantastique (1929), domaine public.', 'https://archive.org/download/ThePhantomOfTheOpera1929/thepaantomoftheopera1929_512kb.mp4', 'mp4', False), ('film-scarlet', 'Scarlet Street', 'Film noir de Fritz Lang (1945), domaine public.', 'https://archive.org/download/scarlet-street-1945-restored-movie-720p-hd/scarlet%20street-1945-restored%20movie-720p-hd.mp4', 'mp4', False), ('film-gaslight', 'Gaslight', 'Mystère psychologique (1940), restauré.', 'https://archive.org/download/gaslight-1940-colorized-movie-576p-sd/gaslight-1940-colorized%20movie-576p-sd.mp4', 'mp4', False)]


def build_rows():
    items = []
    for (iid, title, desc, url, fmt, live) in CONTENU:
        items.append(Item(
            id=iid, title=title, kind='movie', description=desc,
            stream=Stream(url=url, format=fmt, live=live),
            audio_only=(fmt == "mp3"),
        ))
    return [Row(id='films', title='Films', items=items)]


if __name__ == "__main__":
    run_engine('films', build_rows)

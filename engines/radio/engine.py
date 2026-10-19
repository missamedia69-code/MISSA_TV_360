#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Engine « radio » — Radio. Éditez CONTENU pour gérer vos sources."""

from __future__ import annotations
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "_core"))
from schema import Item, Row, Stream, run_engine  # noqa: E402

CONTENU = [('radio-groove', 'Groove Salad', 'Webradio ambient — SomaFM.', 'https://ice1.somafm.com/groovesalad-128-mp3', 'mp3', True), ('radio-lush', 'Lush', 'Webradio voix féminines — SomaFM.', 'https://ice1.somafm.com/lush-128-mp3', 'mp3', True), ('radio-drone', 'Drone Zone', 'Webradio ambient spatial — SomaFM.', 'https://ice1.somafm.com/dronezone-128-mp3', 'mp3', True)]


def build_rows():
    items = []
    for (iid, title, desc, url, fmt, live) in CONTENU:
        items.append(Item(
            id=iid, title=title, kind='radio', description=desc,
            stream=Stream(url=url, format=fmt, live=live),
            audio_only=(fmt == "mp3"),
            thumbnail_url=f"https://placehold.co/320x180/1a237e/ffd600?text={title.replace(" ", "+")}",
        ))
    return [Row(id='radio', title='Radio', items=items)]


if __name__ == "__main__":
    run_engine('radio', build_rows)

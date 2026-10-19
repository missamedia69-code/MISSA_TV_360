#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Engine « tv_direct » — TV Direct. Éditez CONTENU pour gérer vos sources."""

from __future__ import annotations
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "_core"))
from schema import Item, Row, Stream, run_engine  # noqa: E402

CONTENU = [('live-nasa', 'NASA TV Public', 'Chaîne publique de la NASA en flux HLS continu.', 'https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8', 'hls', True), ('live-bipbop', 'BipBop Advanced', "Flux HLS de test d'Apple (multi-débit).", 'https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8', 'hls', True)]


def build_rows():
    items = []
    for (iid, title, desc, url, fmt, live) in CONTENU:
        items.append(Item(
            id=iid, title=title, kind='live_tv', description=desc,
            stream=Stream(url=url, format=fmt, live=live),
            audio_only=(fmt == "mp3"),
            thumbnail_url=f"https://placehold.co/320x180/1a237e/ffd600?text={title.replace(" ", "+")}",
        ))
    return [Row(id='tv_direct', title='TV Direct', items=items)]


if __name__ == "__main__":
    run_engine('tv_direct', build_rows)

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Engine « radio » — Radio. Éditez CONTENU pour gérer vos sources."""

from __future__ import annotations
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "_core"))
from schema import Item, Row, Stream, run_engine  # noqa: E402

# Flux radio publics en écoute libre (sources ouvertes publiques)
CONTENU = [
    ('radio-fip', 'FIP Radio', 'Webradio musicale et éclectique publique.', 'https://icecast.radiofrance.fr/fip-midfi.mp3', 'mp3', True),
    ('radio-couleur3', 'RTS Couleur 3', 'Webradio suisse musique et culture.', 'https://stream.srg-ssr.ch/m/couleur3/mp3_128', 'mp3', True),
    ('radio-kexp', 'KEXP 90.3 FM', 'Radio indépendante musicale de Seattle.', 'https://kexp-mp3-128.streamguys1.com/kexp128.mp3', 'mp3', True)
]


def build_rows():
    items = []
    for (iid, title, desc, url, fmt, live) in CONTENU:
        items.append(Item(
            id=iid, title=title, kind='radio', description=desc,
            stream=Stream(url=url, format=fmt, live=live),
            audio_only=(fmt == "mp3"),
            thumbnail_url=f"https://placehold.co/320x180/1a237e/ffd600?text={title.replace(' ', '+')}",
            source="Webradio Publique",
            license="Diffusion publique libre",
        ))
    return [Row(id='radio', title='Radio', items=items)]


if __name__ == "__main__":
    run_engine('radio', build_rows)

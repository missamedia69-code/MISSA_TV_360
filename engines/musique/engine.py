#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Engine « musique » — Musique sous licence Creative Commons."""

from __future__ import annotations
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "_core"))
from schema import Item, placeholder_thumbnail, Row, run_engine, Stream  # noqa: E402
from sources import internet_archive_audio, jamendo_tracks  # noqa: E402

# Repli quand aucune clé Jamendo n'est configurée : on garde un emplacement
# explicite plutôt qu'une grille vide.
CONTENU = [('music-demo-1', 'Musique · Emplacement 1', 'Emplacement réservé musique (contenu de test — configurez JAMENDO_CLIENT_ID pour activer le catalogue Creative Commons).', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WhatCarCanYouGetForAGrand.mp4', 'mp4', False)]


def remote_items():
    """Pistes sous licence explicite : Jamendo (clé requise) + netlabels IA.

    Sans `JAMENDO_CLIENT_ID`, seule la branche Internet Archive alimente la
    grille ; si les deux échouent, on garde l'emplacement de repli.
    """
    items = []
    for track in jamendo_tracks(limit=6) + internet_archive_audio(limit=6):
        items.append(Item(
            id=track["id"], title=track["title"], kind='music',
            description=track["description"],
            stream=Stream(url=track["url"], format='mp3', live=False),
            audio_only=True,
            thumbnail_url=track["thumbnail"]
            or placeholder_thumbnail(track["title"], "music"),
            source=track["source"],
            license=track["license"],
            demo=False,
            rights_checked=True,
        ))
    return items


def build_rows():
    items = remote_items()
    if not items:
        for (iid, title, desc, url, fmt, live) in CONTENU:
            items.append(Item(
                id=iid, title=title, kind='music', description=desc,
                stream=Stream(url=url, format=fmt, live=live),
                audio_only=(fmt == "mp3"),
                thumbnail_url=placeholder_thumbnail(title, "music"),
                source="Démonstration",
                license="Libre accès (test)",
                demo=True,
                rights_checked=False,
            ))
    return [Row(id='musique', title='Musique', items=items)]


if __name__ == "__main__":
    run_engine('musique', build_rows)

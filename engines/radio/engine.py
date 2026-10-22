#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Engine « radio » — Radio. Éditez CONTENU pour gérer vos sources."""

from __future__ import annotations
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "_core"))
from schema import Item, placeholder_thumbnail, Row, run_engine, Stream  # noqa: E402
from sources import radio_browser_stations  # noqa: E402

# Flux radio publics en écoute libre (sources ouvertes publiques — droits vérifiés)
CONTENU = [
    ('radio-fip', 'FIP Radio', 'Webradio musicale et éclectique publique (audio en continu, non direct vidéo).', 'https://icecast.radiofrance.fr/fip-midfi.mp3', 'mp3', False),
    ('radio-couleur3', 'RTS Couleur 3', 'Webradio suisse musique et culture (audio en continu, non direct vidéo).', 'https://stream.srg-ssr.ch/m/couleur3/mp3_128', 'mp3', False),
    ('radio-kexp', 'KEXP 90.3 FM', 'Radio indépendante musicale de Seattle (audio en continu, non direct vidéo).', 'https://kexp-mp3-128.streamguys1.com/kexp128.mp3', 'mp3', False),
]


def remote_items():
    """Complète la grille avec des webradios listées publiquement.

    Radio Browser n'est qu'un annuaire : chaque station reste diffusée par son
    propre éditeur. On marque donc la provenance, et `rights_checked=False`
    dit honnêtement que les droits de diffusion n'ont pas été vérifiés
    individuellement (contrairement aux trois flux statiques ci-dessus).
    """
    items = []
    for station in radio_browser_stations(limit=12):
        items.append(Item(
            id=station["id"], title=station["title"], kind='radio',
            description=station["description"],
            stream=Stream(url=station["url"], format='mp3', live=False),
            audio_only=True,
            thumbnail_url=placeholder_thumbnail(station["title"], "radio"),
            source="Radio Browser",
            license="Flux public (Radio Browser)",
            demo=False,
            rights_checked=False,
        ))
    return items


def build_rows():
    items = []
    for (iid, title, desc, url, fmt, live) in CONTENU:
        items.append(Item(
            id=iid, title=title, kind='radio', description=desc,
            stream=Stream(url=url, format=fmt, live=live),
            audio_only=(fmt == "mp3"),
            thumbnail_url=placeholder_thumbnail(title, "radio"),
            source="Webradio Publique",
            license="Diffusion publique libre",
            demo=False,
            rights_checked=True,
        ))
    # Une même station peut être listée par Radio Browser : on déduplique sur
    # l'URL de flux pour ne pas afficher deux fois le même direct.
    known_urls = {it.stream.url for it in items if it.stream}
    for item in remote_items():
        if item.stream and item.stream.url not in known_urls:
            known_urls.add(item.stream.url)
            items.append(item)
    return [Row(id='radio', title='Radio', items=items)]


if __name__ == "__main__":
    run_engine('radio', build_rows)

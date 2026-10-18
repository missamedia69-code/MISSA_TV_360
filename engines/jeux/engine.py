#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Engine « jeux » — catalogue des jeux téléchargeables.

Ce moteur est le vrai « moteur » de la catégorie Jeux vidéo : il produit le
catalogue de jeux que l'application affiche et que l'utilisateur installe.

Source de vérité : un fichier manifest YAML/JSON simple dans engines/jeux/jeux.yaml
listant chaque jeu (id, titre, description, fichier/APK, checksum, taille, version).
Le dépôt GitHub stocke les binaires dans les Releases ; le manifest pointe vers
l'URL de téléchargement.

Générez le sha256 avec :
    python engines/jeux/engine.py --hash chemin/vers/fichier.apk
"""

from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "_core"))
from schema import Item, Row, run_engine, sha256_file  # noqa: E402

MANIFEST = os.path.join(os.path.dirname(__file__), "jeux.yaml")


def load_manifest() -> list[dict]:
    """Charge le manifeste des jeux. YAML si PyYAML dispo, sinon JSON équivalent."""
    if not os.path.exists(MANIFEST):
        return []
    try:
        import yaml  # type: ignore
        with open(MANIFEST, "r", encoding="utf-8") as f:
            return yaml.safe_load(f) or []
    except ImportError:
        # Repli : manifeste JSON à côté (jeux.json)
        import json
        alt = MANIFEST.rsplit(".", 1)[0] + ".json"
        if os.path.exists(alt):
            with open(alt, "r", encoding="utf-8") as f:
                return json.load(f)
        print("PyYAML absent et pas de jeux.json — moteur vide.", file=sys.stderr)
        return []


def build_rows() -> list[Row]:
    entries = load_manifest()
    items: list[Item] = []
    for e in entries:
        items.append(Item(
            id=e["id"],
            title=e["title"],
            kind="game",
            description=e.get("description", ""),
            thumbnail_url=e.get("thumbnail_url"),
            download_url=e.get("download_url"),
            size_bytes=e.get("size_bytes"),
            sha256=e.get("sha256"),
            package_type=e.get("package_type", "apk"),
            version_name=e.get("version_name"),
            version_code=e.get("version_code"),
            genre=e.get("genre"),
        ))
    return [Row(id="jeux", title="Jeux vidéo", items=items)]


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--hash":
        print(sha256_file(sys.argv[2]))
    else:
        run_engine("jeux", build_rows)

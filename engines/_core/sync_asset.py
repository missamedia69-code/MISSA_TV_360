#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Synchronise les fichiers publiés avec les assets embarqués de l'app Android.

L'app lit normalement le catalogue servi par GitHub Pages. Hors ligne — ou
quand le réseau ne joint pas Pages — elle retombe sur le catalogue embarqué
dans l'APK. Cet asset doit donc être **identique** à ce que les moteurs
produisent, sinon l'app affiche autre chose que ce que le dépôt publie.

Ce module vit dans `engines/_core/` (versionné) et non dans `tools/` (ignoré
par git) : `build_all.py` peut ainsi l'appeler aussi bien en local qu'en CI.

    engines/*/engine.py  ->  site/data/*.json  ->  site/data/catalog.json
                                              \\-> app/src/main/assets/catalog.json
"""

from __future__ import annotations

import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# (source, destination) — chemins relatifs à la racine du dépôt.
PAIRS = (
    (
        os.path.join("site", "data", "catalog.json"),
        os.path.join("app", "src", "main", "assets", "catalog.json"),
    ),
    (
        os.path.join("engines", "films", "manifest.json"),
        os.path.join("app", "src", "main", "assets", "engines", "films", "manifest.json"),
    ),
    # GitHub Pages sert `site/` : le manifeste doit y être publié comme le fait
    # déjà la CI, sinon un aperçu local teste le repli embarqué au lieu du
    # manifeste distant.
    (
        os.path.join("engines", "films", "manifest.json"),
        os.path.join("site", "engines", "films", "manifest.json"),
    ),
)


def _same_content(source: str, destination: str) -> bool:
    try:
        with open(source, "rb") as left, open(destination, "rb") as right:
            return left.read() == right.read()
    except OSError:
        return False


def sync(verbose: bool = True) -> list[str]:
    """Copie les fichiers publiés vers les assets de l'app. Retourne les copies."""
    copied: list[str] = []
    for relative_source, relative_destination in PAIRS:
        source = os.path.join(ROOT, relative_source)
        destination = os.path.join(ROOT, relative_destination)
        if not os.path.isfile(source):
            print(f"Absent, ignoré : {relative_source}", file=sys.stderr)
            continue
        os.makedirs(os.path.dirname(destination), exist_ok=True)
        if _same_content(source, destination):
            if verbose:
                print(f"Inchangé : {relative_destination}")
            continue
        shutil.copyfile(source, destination)
        copied.append(relative_destination)
        if verbose:
            print(f"Copié    : {relative_source} -> {relative_destination}")
    return copied

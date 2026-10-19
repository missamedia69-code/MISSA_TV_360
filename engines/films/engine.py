#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Engine « films » — test d'un fournisseur Internet Archive.

Le prototype ne parcourt pas tout Internet Archive : il résout le titre IA
qui figurait déjà dans le catalogue MISSA, puis conserve les entrées existantes
comme solution de repli si l'API est momentanément indisponible.
"""

from __future__ import annotations

import html
import json
import os
import re
import sys
from typing import Any
from urllib.parse import quote
from urllib.request import Request, urlopen

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "_core"))
from schema import Item, Row, Stream, run_engine  # noqa: E402

# Premier test limité à un item IA déjà référencé dans le catalogue MISSA.
ARCHIVE_IDENTIFIER = "ThePhantomOfTheOpera1929"
ARCHIVE_ITEM_ID = "film-phantom"
ARCHIVE_FALLBACK_TITLE = "Phantom of the Opera"
ARCHIVE_FALLBACK_DESCRIPTION = "Film muet fantastique référencé sur Internet Archive."
ARCHIVE_FALLBACK_URL = (
    "https://archive.org/download/ThePhantomOfTheOpera1929/"
    "thepaantomoftheopera1929_512kb.mp4"
)

# Complète le test avec les fiches IA déjà présentes dans le catalogue local
# MISSA ; chaque vignette vient de l'endpoint d'image du même item IA.
CONTENU_STATIQUE = [
    (
        "film-scarlet",
        "Scarlet Street",
        "Film noir de Fritz Lang (1945), restauré, domaine public. Note IMDB 7.8.",
        "https://archive.org/download/scarlet-street-1945-restored-movie-720p-hd/"
        "scarlet%20street-1945-restored%20movie-720p-hd.mp4",
        "scarlet-street-1945-restored-movie-720p-hd",
    ),
    (
        "film-gaslight",
        "Gaslight",
        "Mystère psychologique de George Cukor (1940), restauré et colorisé.",
        "https://archive.org/download/gaslight-1940-colorized-movie-576p-sd/"
        "gaslight-1940-colorized%20movie-576p-sd.mp4",
        "gaslight-1940-colorized-movie-576p-sd",
    ),
    (
        "film-mrx",
        "The Amazing Mr. X",
        "Film noir mystérieux de Harold Schuster (1948), restauré.",
        "https://archive.org/download/the-amazing-mr.-x-1948-restored-movie-720p-hd/"
        "The%20Amazing%20Mr.%20X-1948-restored%20movie-720p-hd.mp4",
        "the-amazing-mr.-x-1948-restored-movie-720p-hd",
    ),
    (
        "film-sanfran",
        "Old San Francisco",
        "Drame mélodramatique d'Alan Crosland (1927), domaine public.",
        "https://archive.org/download/old-san-francisco-1927-dvd-mkv/"
        "Old%20San%20Francisco%20(1927)%20%7Btmdb-104433%7D%20%5BDVD%5D"
        "%5BAC3%202.0%5D%5BMPEG2%5D.mp4",
        "old-san-francisco-1927-dvd-mkv",
    ),
    (
        "film-hometown",
        "Home Town Story",
        "Drame familial américain d'Arthur Lubin (1951), domaine public.",
        "https://archive.org/download/home-town-story-1951-classic-family-drama/"
        "Home%20Town%20Story%20(1951)%20%2B%2B%2B%2B%2B%20Classic%20Family%20Drama).mp4",
        "home-town-story-1951-classic-family-drama",
    ),
]


def _as_int(value: Any) -> int:
    try:
        return int(value or 0)
    except (TypeError, ValueError):
        return 0


def _clean_description(value: Any) -> str:
    if isinstance(value, list):
        value = " ".join(str(part) for part in value)
    if not isinstance(value, str):
        return ""
    text = html.unescape(re.sub(r"<[^>]*>", " ", value))
    return re.sub(r"\s+", " ", text).strip()[:600]


def parse_archive_item(identifier: str, payload: dict[str, Any]) -> Item | None:
    """Convertit la réponse JSON IA en fiche MISSA, sans dépendance externe."""
    metadata = payload.get("metadata") or {}
    files = payload.get("files") or []
    if not isinstance(metadata, dict) or not isinstance(files, list):
        return None

    playable: list[dict[str, Any]] = []
    for media_file in files:
        if not isinstance(media_file, dict):
            continue
        name = media_file.get("name")
        if not isinstance(name, str) or not name.lower().endswith(".mp4"):
            continue
        # Refuse les segments de chemin qui pourraient sortir du dossier du item.
        parts = name.replace("\\", "/").split("/")
        if not name or any(part in {".", ".."} for part in parts):
            continue
        playable.append(media_file)

    if not playable:
        return None

    # Si plusieurs MP4 sont présents, choisit le meilleur fichier d'après la
    # hauteur déclarée, puis la taille, sans ajouter de bibliothèque vidéo.
    selected = max(
        playable,
        key=lambda entry: (_as_int(entry.get("height")), _as_int(entry.get("size"))),
    )
    filename = selected["name"]
    encoded_identifier = quote(identifier, safe="-._~")
    encoded_filename = quote(filename, safe="/-._~()@:+")
    title = str(metadata.get("title") or ARCHIVE_FALLBACK_TITLE).strip()
    description = _clean_description(metadata.get("description"))

    return Item(
        id=ARCHIVE_ITEM_ID,
        title=title or ARCHIVE_FALLBACK_TITLE,
        kind="movie",
        description=description or ARCHIVE_FALLBACK_DESCRIPTION,
        thumbnail_url=f"https://archive.org/services/img/{encoded_identifier}",
        stream=Stream(
            url=f"https://archive.org/download/{encoded_identifier}/{encoded_filename}",
            format="mp4",
            live=False,
        ),
    )


def fetch_archive_payload(identifier: str) -> dict[str, Any]:
    """Récupère les métadonnées publiques IA d'un identifiant précis."""
    url = f"https://archive.org/metadata/{quote(identifier, safe='-._~')}"
    request = Request(
        url,
        headers={
            "Accept": "application/json",
            "User-Agent": "MISSA-TV-360-catalog-builder/1.0",
        },
    )
    with urlopen(request, timeout=12) as response:
        payload = json.load(response)
    if not isinstance(payload, dict):
        raise ValueError("Réponse de métadonnées Internet Archive invalide")
    return payload


def build_archive_test_item() -> Item:
    """Résout le titre test; revient à l'URL déjà publiée en cas d'échec réseau."""
    try:
        item = parse_archive_item(
            ARCHIVE_IDENTIFIER,
            fetch_archive_payload(ARCHIVE_IDENTIFIER),
        )
        if item is not None:
            return item
        raise ValueError("aucun fichier MP4 lisible dans les métadonnées")
    except Exception as exc:
        print(
            f"Avertissement : résolution IA impossible ({exc}); "
            "conservation du flux déjà configuré.",
            file=sys.stderr,
        )
        return Item(
            id=ARCHIVE_ITEM_ID,
            title=ARCHIVE_FALLBACK_TITLE,
            kind="movie",
            description=ARCHIVE_FALLBACK_DESCRIPTION,
            thumbnail_url=f"https://archive.org/services/img/{ARCHIVE_IDENTIFIER}",
            stream=Stream(url=ARCHIVE_FALLBACK_URL, format="mp4", live=False),
        )


def _build_internet_archive_source() -> list[Item]:
    """Compile la source IA en fiches normalisées pour le catalogue MISSA."""
    items = [build_archive_test_item()]
    items.extend(
        Item(
            id=item_id,
            title=title,
            kind="movie",
            description=description,
            thumbnail_url=(
                f"https://archive.org/services/img/{quote(archive_id, safe='-._~')}"
            ),
            stream=Stream(url=url, format="mp4", live=False),
        )
        for item_id, title, description, url, archive_id in CONTENU_STATIQUE
    )
    return items


# Registre d'agrégation par source : chaque fournisseur ajoute une fonction
# qui traduit ses contenus en Item; d'autres sources pourront être branchées ici.
FILM_SOURCES = (_build_internet_archive_source,)


def build_rows() -> list[Row]:
    items = [item for source_builder in FILM_SOURCES for item in source_builder()]
    return [Row(id="films", title="Films", items=items)]


if __name__ == "__main__":
    run_engine("films", build_rows)

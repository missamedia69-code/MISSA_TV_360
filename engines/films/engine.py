#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Engine « films » — sélection éditoriale de films du domaine public.

Deux responsabilités bien séparées :

* la **curation** (titre, description, identifiant Internet Archive) est
  éditoriale et versionnée dans ce fichier ;
* l'**URL de lecture** est *auto-réparante* : elle est résolue à chaque build
  auprès de l'API de métadonnées d'Internet Archive (meilleur MP4 par hauteur
  puis taille). Une URL périmée est donc corrigée au build suivant au lieu de
  casser la lecture.

Si le réseau est indisponible (CI hors ligne, coupure), chaque film conserve
son URL de repli : le build produit toujours un catalogue valide.
"""

from __future__ import annotations

import html
import json
import os
import re
import sys
from typing import Any, Optional
from urllib.parse import quote
from urllib.request import Request, urlopen

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "_core"))
from schema import Item, placeholder_thumbnail, Row, run_engine, Stream  # noqa: E402

# ── Configuration ────────────────────────────────────────────────────────────

# Les sondes réseau peuvent être désactivées pour un build hors-ligne ou
# pour les tests :  MISSA_ENGINE_OFFLINE=1 python engines/films/engine.py
OFFLINE = os.environ.get("MISSA_ENGINE_OFFLINE", "").strip().lower() in {"1", "true", "yes"}

HTTP_TIMEOUT = 10

# Libellés affichés sur la fiche détails de l'app.
SOURCE_LABEL = "Internet Archive"
LICENSE_LABEL = "Domaine public"

# Film témoin historique : il sert de sonde complète (métadonnées IA utilisées
# telles quelles) et de témoin de non-régression pour le reste du moteur.
ARCHIVE_IDENTIFIER = "ThePhantomOfTheOpera1929"
ARCHIVE_ITEM_ID = "film-phantom"
ARCHIVE_FALLBACK_TITLE = "Phantom of the Opera"
ARCHIVE_FALLBACK_DESCRIPTION = "Film muet fantastique référencé sur Internet Archive."
ARCHIVE_FALLBACK_URL = (
    "https://archive.org/download/ThePhantomOfTheOpera1929/"
    "thepaantomoftheopera1929_512kb.mp4"
)

# ── Curation éditoriale ──────────────────────────────────────────────────────
# (identifiant catalogue, titre, description, identifiant IA, URL de repli)
CURATION: list[tuple[str, str, str, str, str]] = [
    (
        "film-phantom",
        "The Phantom of the Opera (1929)",
        "Film muet fantastique de Rupert Julian (1929), domaine public.",
        ARCHIVE_IDENTIFIER,
        ARCHIVE_FALLBACK_URL,
    ),
    (
        "film-scarlet",
        "Scarlet Street",
        "Film noir de Fritz Lang (1945), restauré, domaine public.",
        "scarlet-street-1945-restored-movie-720p-hd",
        "https://archive.org/download/scarlet-street-1945-restored-movie-720p-hd/"
        "scarlet%20street-1945-restored%20movie-720p-hd.mp4",
    ),
    (
        "film-gaslight",
        "Gaslight",
        "Mystère psychologique de George Cukor (1940), restauré et colorisé.",
        "gaslight-1940-colorized-movie-576p-sd",
        "https://archive.org/download/gaslight-1940-colorized-movie-576p-sd/"
        "gaslight-1940-colorized%20movie-576p-sd.mp4",
    ),
    (
        "film-mrx",
        "The Amazing Mr. X",
        "Film noir mystérieux de Harold Schuster (1948), restauré.",
        "the-amazing-mr.-x-1948-restored-movie-720p-hd",
        "https://archive.org/download/the-amazing-mr.-x-1948-restored-movie-720p-hd/"
        "The%20Amazing%20Mr.%20X-1948-restored%20movie-720p-hd.mp4",
    ),
    (
        "film-sanfran",
        "Old San Francisco",
        "Drame mélodramatique d'Alan Crosland (1927), domaine public.",
        "old-san-francisco-1927-dvd-mkv",
        "https://archive.org/download/old-san-francisco-1927-dvd-mkv/"
        "Old%20San%20Francisco%20(1927)%20%7Btmdb-104433%7D%20%5BDVD%5D"
        "%5BAC3%202.0%5D%5BMPEG2%5D.mp4",
    ),
    (
        "film-hometown",
        "Home Town Story",
        "Drame familial américain d'Arthur Lubin (1951), domaine public.",
        "home-town-story-1951-classic-family-drama",
        "https://archive.org/download/home-town-story-1951-classic-family-drama/"
        "Home%20Town%20Story%20(1951)%20%2B%2B%2B%2B%2B%20Classic%20Family%20Drama).mp4",
    ),
    (
        "film-nosferatu",
        "Nosferatu (1922)",
        "Chef-d'œuvre expressionniste de F. W. Murnau (1922), domaine public.",
        # L'item « nosferatu » renvoie un 403 sur /services/img/ (pas de
        # poster) : on cible un item équivalent qui expose poster ET MP4.
        "nosferatu-1922_202507",
        "https://archive.org/download/nosferatu-1922_202507/"
        "Nosferatu%20%281922%29.mp4",
    ),
    (
        "film-night-of-living-dead",
        "Night of the Living Dead (1968)",
        "Film fondateur de George A. Romero (1968), domaine public.",
        # Idem : « night_of_the_living_dead » n'expose pas de poster.
        "night-of-the-living-dead-extended-version",
        "https://archive.org/download/night-of-the-living-dead-extended-version/"
        "Night%20of%20the%20Living%20Dead%20Extended%20Version.ia.mp4",
    ),
    (
        "film-detour",
        "Detour (1945)",
        "Road movie noir d'Edgar G. Ulmer (1945), domaine public.",
        "Detour_1945",
        "https://archive.org/download/Detour_1945/Detour_1945_512kb.mp4",
    ),
    (
        "film-the-general",
        "The General (1926)",
        "Comédie burlesque de Buster Keaton (1926), domaine public.",
        "TheGeneral",
        "https://archive.org/download/TheGeneral/The_General_512kb.mp4",
    ),
    (
        "film-his-girl-friday",
        "His Girl Friday (1940)",
        "Comédie de Howard Hawks (1940), domaine public.",
        "his_girl_friday",
        "https://archive.org/download/his_girl_friday/his_girl_friday_512kb.mp4",
    ),
]


# ── Utilitaires ──────────────────────────────────────────────────────────────

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


def thumbnail_url(identifier: str) -> str:
    """URL de la vignette servie par Internet Archive pour un identifiant."""
    return f"https://archive.org/services/img/{quote(identifier, safe='-._~')}"


def thumbnail_is_reachable(identifier: str) -> bool:
    """Vrai si la vignette Internet Archive est téléchargeable.

    Certains items du domaine public renvoient un 403 sur ``/services/img/``
    (poster absent ou accès restreint). Sans cette vérification, la tuile
    resterait vide côté application : on bascule alors sur une vignette de
    repli locale et décodable.
    """
    if OFFLINE:
        # Hors ligne, on ne peut pas trancher : on garde l'URL IA (le build
        # produit toujours un catalogue valide).
        return True
    request = Request(
        thumbnail_url(identifier),
        headers={"User-Agent": "MISSA-TV-360-catalog-builder/1.0"},
    )
    try:
        with urlopen(request, timeout=HTTP_TIMEOUT) as response:
            content_type = response.headers.get("Content-Type", "").lower()
            return response.status == 200 and content_type.startswith("image/")
    except Exception:
        return False


def resolved_thumbnail(identifier: str, title: str) -> str:
    """Vignette du film : poster IA s'il est accessible, repli sinon."""
    if thumbnail_is_reachable(identifier):
        return thumbnail_url(identifier)
    print(
        f"Vignette IA indisponible pour {identifier} ; repli local utilisé.",
        file=sys.stderr,
    )
    return placeholder_thumbnail(title, "movie")


def download_url(identifier: str, filename: str) -> str:
    """URL de téléchargement d'un fichier d'un item IA."""
    return (
        f"https://archive.org/download/{quote(identifier, safe='-._~')}"
        f"/{quote(filename, safe='/-._~()@:+')}"
    )


def license_of(payload: dict[str, Any]) -> str:
    """Libellé de licence déclaré par un item IA (chaîne vide si absent)."""
    metadata = payload.get("metadata") or {}
    if not isinstance(metadata, dict):
        return ""
    for key in ("rights", "license", "licenseurl", "license_url"):
        value = metadata.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return ""


def _license_is_free(rights: str) -> bool:
    """Vrai si la licence déclarée est explicitement libre.

    Les items sans indication de licence sont acceptés : c'est le cas
    historique des entrées du domaine public sur Internet Archive.
    """
    if not rights.strip():
        return True
    low = rights.lower()
    return any(token in low for token in (
        "public domain", "creative commons", "cc0", "cc by", "cc-by", "cc0 1.0",
    ))


def playable_files(files: Any) -> list[dict[str, Any]]:
    """Fichiers MP4 jouables d'un item IA, du meilleur au moins bon.

    Un MP4 est retenu si son nom ne contient aucun segment de chemin
    permettant de sortir du dossier de l'item.
    """
    if not isinstance(files, list):
        return []
    playable: list[dict[str, Any]] = []
    for media_file in files:
        if not isinstance(media_file, dict):
            continue
        name = media_file.get("name")
        if not isinstance(name, str) or not name.lower().endswith(".mp4"):
            continue
        parts = name.replace("\\", "/").split("/")
        if not name or any(part in {".", ".."} for part in parts):
            continue
        playable.append(media_file)
    # Hauteur déclarée d'abord, puis taille : le meilleur fichier disponible.
    playable.sort(key=lambda entry: (_as_int(entry.get("height")), _as_int(entry.get("size"))))
    playable.reverse()
    return playable


def select_playable(payload: dict[str, Any]) -> Optional[str]:
    """Nom du meilleur MP4 d'un item IA (None si aucun)."""
    playable = playable_files(payload.get("files"))
    if not playable:
        return None
    return str(playable[0]["name"])


# ── Conversion IA ───────────────────────────────────────────────────────────

def parse_archive_item(
    identifier: str,
    payload: dict[str, Any],
    *,
    item_id: Optional[str] = None,
    title: Optional[str] = None,
    description: Optional[str] = None,
) -> Item | None:
    """Convertit la réponse JSON IA en fiche MISSA, sans dépendance externe.

    Sans surcharge, la fiche reprend les métadonnées IA (titre, description).
    Avec surcharge (curation éditoriale), ces champs remplacent les
    métadonnées IA ; seule l'URL de lecture vient de la source.
    """
    metadata = payload.get("metadata") or {}
    files = payload.get("files") or []
    if not isinstance(metadata, dict) or not isinstance(files, list):
        return None

    # Filtre les droits : on ne conserve que les contenus dont la licence est
    # explicitement libre (domaine public / Creative Commons). Les items sans
    # indication de licence sont conservés (cas historique du catalogue).
    if not _license_is_free(license_of(payload)):
        return None

    filename = select_playable(payload)
    if filename is None:
        return None

    ia_title = str(metadata.get("title") or ARCHIVE_FALLBACK_TITLE).strip()
    ia_description = _clean_description(metadata.get("description"))

    return Item(
        id=item_id or ARCHIVE_ITEM_ID,
        title=(title if title is not None else ia_title) or ARCHIVE_FALLBACK_TITLE,
        kind="movie",
        description=(description if description is not None else ia_description)
            or ARCHIVE_FALLBACK_DESCRIPTION,
        thumbnail_url=resolved_thumbnail(
            identifier, (title if title is not None else ia_title) or ARCHIVE_FALLBACK_TITLE
        ),
        stream=Stream(
            url=download_url(identifier, filename),
            format="mp4",
            live=False,
        ),
        source=SOURCE_LABEL,
        license=LICENSE_LABEL,
        demo=False,
        rights_checked=True,
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
    with urlopen(request, timeout=HTTP_TIMEOUT) as response:
        payload = json.load(response)
    if not isinstance(payload, dict):
        raise ValueError("Réponse de métadonnées Internet Archive invalide")
    return payload


# ── Construction des fiches ──────────────────────────────────────────────────

def build_curated_item(
    item_id: str,
    title: str,
    description: str,
    identifier: str,
    fallback_url: str,
) -> Item:
    """Résout l'URL de lecture d'un film curé ; repli sur l'URL de repli."""
    if not OFFLINE:
        try:
            payload = fetch_archive_payload(identifier)
            item = parse_archive_item(
                identifier,
                payload,
                item_id=item_id,
                title=title,
                description=description,
            )
            if item is not None:
                return item
            print(
                f"Avertissement : {identifier} sans MP4 libre lisible ; "
                "URL de repli conservée.",
                file=sys.stderr,
            )
        except Exception as exc:
            print(
                f"Avertissement : résolution IA impossible pour {identifier} ({exc}) ; "
                "URL de repli conservée.",
                file=sys.stderr,
            )
    return Item(
        id=item_id,
        title=title,
        kind="movie",
        description=description,
        thumbnail_url=resolved_thumbnail(identifier, title),
        stream=Stream(url=fallback_url, format="mp4", live=False),
        source=SOURCE_LABEL,
        license=LICENSE_LABEL,
        demo=False,
        rights_checked=True,
    )


def build_archive_test_item() -> Item:
    """Résout le film témoin ; revient à l'URL déjà publiée en cas d'échec."""
    return build_curated_item(
        ARCHIVE_ITEM_ID,
        ARCHIVE_FALLBACK_TITLE,
        ARCHIVE_FALLBACK_DESCRIPTION,
        ARCHIVE_IDENTIFIER,
        ARCHIVE_FALLBACK_URL,
    )


def _build_internet_archive_source() -> list[Item]:
    """Compile la curation IA en fiches normalisées pour le catalogue MISSA."""
    return [
        build_curated_item(item_id, title, description, identifier, fallback_url)
        for item_id, title, description, identifier, fallback_url in CURATION
    ]


# Registre d'agrégation par source : chaque fournisseur ajoute une fonction
# qui traduit ses contenus en Item; d'autres sources pourront être branchées ici.
FILM_SOURCES = (_build_internet_archive_source,)


def build_rows() -> list[Row]:
    items = [item for source_builder in FILM_SOURCES for item in source_builder()]
    return [Row(id="films", title="Films", items=items)]


if __name__ == "__main__":
    run_engine("films", build_rows)

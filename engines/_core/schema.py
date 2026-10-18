#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Moteur commun MISSA TV 360.

Chaque catégorie de l'application a son propre « moteur » (engine) dans engines/<cat>/.
Un moteur est un script Python qui :
  1. rassemble ses contenus (fichiers locaux, sources libres, ou flux externes),
  2. les convertit en JSON conforme au schéma ci-dessous,
  3. écrit le résultat dans site/data/<cat>.json.

La CI GitHub Actions exécute tous les moteurs puis déploie site/ via GitHub Pages.
L'application Android lit ensuite https://<compte>.github.io/<depot>/data/<cat>.json.

Ce module fournit :
  - les dataclasses du schéma (Row / Item / Stream / Game),
  - la validation (checksums, kinds connus, URLs),
  - l'écriture atomique des JSON publiés,
  - un point d'entrée commun `run_engine`.
"""

from __future__ import annotations

import json
import os
import sys
import hashlib
import datetime as _dt
from dataclasses import dataclass, field, asdict
from typing import Optional

# ── Schéma des kinds (doit rester aligné avec ContentKind côté Android) ──────
KNOWN_KINDS = {
    "live_tv", "sport", "movie", "series", "short",
    "radio", "podcast", "music", "kids", "game",
}

DATA_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "site", "data")

# Ordre canonique des 10 lignes du catalogue (imposé par l'app Android).
# Doit rester aligné avec CANONICAL_ORDER dans CatalogRepository.kt.
CANONICAL_ROW_ORDER = [
    "tv_direct", "sport", "films", "series", "films_courts",
    "radio", "podcasts", "musique", "jeunesse", "jeux",
]


def canonical_index(row_id: str) -> int:
    """Position d'une ligne dans l'ordre canonique (inconnues à la fin)."""
    try:
        return CANONICAL_ROW_ORDER.index(row_id)
    except ValueError:
        return len(CANONICAL_ROW_ORDER)


# ── Modèle ───────────────────────────────────────────────────────────────────
@dataclass
class Stream:
    """Référence de lecture pour un conteneur de média."""
    url: str
    format: str = "mp4"          # mp4 | hls | mp3
    live: bool = False


@dataclass
class Item:
    """Un contenu affichable sur une carte."""
    id: str
    title: str
    kind: str                    # un membre de KNOWN_KINDS
    description: str = ""
    thumbnail_url: Optional[str] = None
    stream: Optional[Stream] = None
    series_title: Optional[str] = None
    season_number: Optional[int] = None
    episode_number: Optional[int] = None
    duration_sec: Optional[int] = None
    audio_only: bool = False
    # Jeux téléchargeables uniquement :
    download_url: Optional[str] = None
    size_bytes: Optional[int] = None
    sha256: Optional[str] = None
    package_type: Optional[str] = None   # apk | asset | web
    version_name: Optional[str] = None
    version_code: Optional[int] = None
    genre: Optional[str] = None


@dataclass
class Row:
    """Une ligne de catalogue (une catégorie de l'application)."""
    id: str
    title: str
    items: list[Item] = field(default_factory=list)


# ── Validation ───────────────────────────────────────────────────────────────
def validate_row(row: Row) -> list[str]:
    """Retourne la liste des erreurs d'un row (vide si valide)."""
    errs: list[str] = []
    if not row.id:
        errs.append("row.id vide")
    for it in row.items:
        if not it.id:
            errs.append(f"[{row.title}] item sans id")
        if it.kind not in KNOWN_KINDS:
            errs.append(f"[{row.title}/{it.id}] kind inconnu : {it.kind!r}")
        if it.kind == "game":
            if not it.download_url:
                errs.append(f"[{row.title}/{it.id}] jeu sans download_url")
        else:
            if not it.stream or not it.stream.url:
                errs.append(f"[{row.title}/{it.id}] item sans stream.url")
            elif it.stream.format not in {"mp4", "hls", "mp3"}:
                errs.append(f"[{row.title}/{it.id}] format inconnu : {it.stream.format!r}")
    return errs


# ── Écriture des JSON publiés ────────────────────────────────────────────────
def _json_default(o):
    if isinstance(o, (_dt.date, _dt.datetime)):
        return o.isoformat()
    raise TypeError(f"Non sérialisable : {type(o)}")


def write_catalog(rows: list[Row], out_dir: str = DATA_DIR, name: str = "catalog") -> str:
    """Valide puis écrit <out_dir>/<name>.json. Lève SystemExit si invalide."""
    os.makedirs(out_dir, exist_ok=True)

    all_errs: list[str] = []
    for row in rows:
        all_errs.extend(validate_row(row))
    if all_errs:
        print("Catalogue invalide :", file=sys.stderr)
        for e in all_errs:
            print("  - " + e, file=sys.stderr)
        raise SystemExit(1)

    payload = {
        "generated_at": _dt.datetime.now(_dt.timezone.utc).isoformat(),
        "rows": [asdict(r) for r in rows],
    }
    path = os.path.join(out_dir, f"{name}.json")
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2, default=_json_default)
    os.replace(tmp, path)
    print(f"Écrit : {path}  ({len(rows)} lignes, "
          f"{sum(len(r.items) for r in rows)} items)")
    return path


def consolidate(out_dir: str = DATA_DIR) -> str:
    """Fusionne tous les <cat>.json de site/data/ en un catalog.json unique."""
    import glob
    rows: list[dict] = []
    for p in sorted(glob.glob(os.path.join(out_dir, "*.json"))):
        if os.path.basename(p) == "catalog.json":
            continue
        with open(p, "r", encoding="utf-8") as f:
            data = json.load(f)
        rows.extend(data.get("rows", []))
    # Tri selon l'ordre canonique des 10 catégories (l'app s'attend à cet ordre).
    rows.sort(key=lambda r: canonical_index(r.get("id", "")))
    payload = {
        "generated_at": _dt.datetime.now(_dt.timezone.utc).isoformat(),
        "rows": rows,
    }
    path = os.path.join(out_dir, "catalog.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2, default=_json_default)
    print(f"Consolidé : {path}  ({len(rows)} lignes)")
    return path


def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest()


def run_engine(name: str, build_rows) -> None:
    """Point d'entrée commun d'un moteur. `build_rows()` retourne list[Row].
    Écrit site/data/<name>.json (un fichier par moteur, pas de conflit)."""
    print(f"── Engine {name} ──")
    rows = build_rows()
    write_catalog(rows, name=name)

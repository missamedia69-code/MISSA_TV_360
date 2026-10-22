#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Construit `site/extensions/plugins.json` à partir des artefacts `.msa`.

Ce script est l'équivalent du `makePluginsJson` d'un dépôt d'extensions
classique : il transforme des fichiers compilés en une liste publiée, en
calculant l'empreinte SHA-256 et la taille de chacun.

Contrat attendu par l'application (`PluginEntry`, voir
`app/src/main/java/com/missa/tv360/extensions/ExtensionModels.kt`) :

    url, name, internalName, version, status, apiVersion, authors,
    description, iconUrl, language, tvTypes, fileSize, fileHash

Convention de dépôt de build :

    extensions-builds/
        hello-provider.msa     <- artefact (ZIP : manifest.json + classes.dex)
        hello-provider.json    <- métadonnées (name, version, authors, …)

Usage :

    python3 extension-tools/build_extension_repo.py \
        --builds-dir extensions-builds \
        --base-url https://exemple.tld/extensions/builds \
        --out site/extensions/plugins.json

    # Vérifie que le fichier publié est à jour (code de sortie 1 sinon) :
    python3 extension-tools/build_extension_repo.py --check …

Aucune dépendance externe : ce script doit tourner partout, y compris dans un
environnement sans PyYAML.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any, Iterable

# Doit rester aligné sur plugin-api/src/main/kotlin/com/missa/tv360/api/PluginApi.kt
API_VERSION = 1

# Doit rester aligné sur PluginEntry.STATUS_* côté application.
STATUS_DOWN = 0
STATUS_OK = 1
STATUS_SLOW = 2
STATUS_BETA_ONLY = 3
VALID_STATUS = {STATUS_DOWN, STATUS_OK, STATUS_SLOW, STATUS_BETA_ONLY}

SLUG_RE = re.compile(r"^[a-z0-9][a-z0-9._-]{1,63}$")

DEFAULT_STATUS = STATUS_OK


class RepoError(Exception):
    """Erreur de construction du dépôt (message destiné au développeur)."""


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_metadata(path: Path) -> dict[str, Any]:
    try:
        raw = path.read_text(encoding="utf-8")
    except OSError as exc:
        raise RepoError(f"métadonnées illisibles : {path} ({exc})") from exc
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise RepoError(f"JSON invalide dans {path} : {exc}") from exc
    if not isinstance(data, dict):
        raise RepoError(f"{path} doit contenir un objet JSON")
    return data


def validate_base_url(base_url: str, allow_http: bool) -> str:
    base = base_url.strip().rstrip("/")
    if not base:
        raise RepoError("--base-url est obligatoire")
    if base.startswith("https://"):
        return base
    if base.startswith("http://") and allow_http:
        return base
    raise RepoError(
        "l'URL de base doit être en HTTPS "
        "(utilisez --allow-http uniquement pour un test local)"
    )


def build_entry(artifact: Path, meta_path: Path, base_url: str) -> dict[str, Any]:
    meta = load_metadata(meta_path)

    internal_name = str(meta.get("internalName") or artifact.stem).strip()
    if not SLUG_RE.match(internal_name):
        raise RepoError(
            f"{meta_path} : internalName « {internal_name} » invalide "
            "(minuscules, chiffres, '.', '_', '-', 2 à 64 caractères)"
        )

    name = str(meta.get("name") or "").strip()
    if not name:
        raise RepoError(f"{meta_path} : champ « name » obligatoire")

    version = meta.get("version")
    if not isinstance(version, int) or version < 1:
        raise RepoError(f"{meta_path} : « version » doit être un entier >= 1")

    api_version = meta.get("apiVersion", API_VERSION)
    if api_version != API_VERSION:
        raise RepoError(
            f"{meta_path} : apiVersion {api_version} alors que l'application "
            f"embarque l'API {API_VERSION}"
        )

    status = meta.get("status", DEFAULT_STATUS)
    if status not in VALID_STATUS:
        raise RepoError(f"{meta_path} : status {status} hors de {sorted(VALID_STATUS)}")

    file_hash = sha256_file(artifact)
    size = artifact.stat().st_size
    if size == 0:
        raise RepoError(f"{artifact} est vide")

    declared_size = meta.get("fileSize")
    if declared_size is not None and declared_size != size:
        raise RepoError(
            f"{meta_path} : fileSize {declared_size} ≠ taille réelle {size}"
        )

    entry: dict[str, Any] = {
        "url": f"{base_url}/{artifact.name}",
        "name": name,
        "internalName": internal_name,
        "version": version,
        "status": status,
        "apiVersion": api_version,
        "authors": list(meta.get("authors") or []),
        "description": meta.get("description"),
        "iconUrl": meta.get("iconUrl"),
        "language": meta.get("language"),
        "tvTypes": meta.get("tvTypes"),
        "fileSize": size,
        "fileHash": f"sha256-{file_hash}",
    }
    return entry


def collect(builds_dir: Path, base_url: str) -> list[dict[str, Any]]:
    if not builds_dir.is_dir():
        raise RepoError(f"dossier de builds introuvable : {builds_dir}")

    artifacts = sorted(builds_dir.glob("*.msa"))
    if not artifacts:
        return []

    entries: list[dict[str, Any]] = []
    seen: dict[str, Path] = {}
    for artifact in artifacts:
        meta_path = artifact.with_suffix(".json")
        if not meta_path.is_file():
            raise RepoError(
                f"{artifact.name} n'a pas de métadonnées ({meta_path.name}) : "
                "refus de publier un artefact non décrit"
            )
        entry = build_entry(artifact, meta_path, base_url)
        key = entry["internalName"]
        if key in seen:
            raise RepoError(
                f"internalName « {key} » présent deux fois "
                f"({seen[key].name} et {artifact.name})"
            )
        seen[key] = artifact
        entries.append(entry)

    return sorted(entries, key=lambda item: item["internalName"])


def render(entries: Iterable[dict[str, Any]]) -> str:
    return json.dumps(list(entries), indent=2, ensure_ascii=False) + "\n"


def write_output(out: Path, entries: list[dict[str, Any]]) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(render(entries), encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--builds-dir", required=True, type=Path)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument(
        "--check",
        action="store_true",
        help="ne rien écrire : échouer si le fichier publié diffère de la source",
    )
    parser.add_argument(
        "--allow-http",
        action="store_true",
        help="autorise une base http:// (tests locaux uniquement)",
    )
    args = parser.parse_args(argv)

    try:
        base_url = validate_base_url(args.base_url, args.allow_http)
        entries = collect(args.builds_dir, base_url)
    except RepoError as exc:
        print(f"ERREUR : {exc}", file=sys.stderr)
        return 2

    expected = render(entries)

    if args.check:
        if not args.out.is_file():
            print(f"ERREUR : {args.out} absent (lancer sans --check)", file=sys.stderr)
            return 1
        current = args.out.read_text(encoding="utf-8")
        if current != expected:
            print(
                f"ERREUR : {args.out} n'est pas à jour — relancer le script sans --check",
                file=sys.stderr,
            )
            return 1
        print(f"OK : {args.out} à jour ({len(entries)} extension(s))")
        return 0

    write_output(args.out, entries)
    print(f"{len(entries)} extension(s) publiée(s) dans {args.out}")
    for entry in entries:
        print(f"  - {entry['internalName']} v{entry['version']} ({entry['fileSize']} o)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

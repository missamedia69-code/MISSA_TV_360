#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Sources distantes optionnelles, partagées par les moteurs de contenu.

Chaque fonction interroge une **API publique documentée** et renvoie des
dictionnaires prêts à devenir des items. Aucune ne lève d'exception : en cas
d'échec réseau, elle renvoie une liste vide, le moteur garde alors son contenu
statique et le build reste vert (même principe que le repli des vignettes).

Rappel : ces sources ne sont branchées que sur des catalogues dont la licence
est connue (webradios publiques, Creative Commons). Le champ `source` et le
champ `license` de chaque item disent d'où vient le contenu.
"""

from __future__ import annotations

import json
import os
import sys
import urllib.parse
import urllib.request

USER_AGENT = "MISSA-TV-360-catalog-builder/1.0"
TIMEOUT_SECONDS = 12

RADIO_BROWSER_ENDPOINT = "https://de1.api.radio-browser.info/json/stations/search"
JAMENDO_ENDPOINT = "https://api.jamendo.com/v3.0/tracks/"
IA_SEARCH_ENDPOINT = "https://archive.org/advancedsearch.php"
IA_METADATA_ENDPOINT = "https://archive.org/metadata/"


def _get_json(url: str, params: dict | None = None):
    """GET JSON tolérant aux pannes : retourne None si la source ne répond pas."""
    if params:
        url = f"{url}?{urllib.parse.urlencode(params, doseq=True)}"
    request = urllib.request.Request(
        url,
        headers={"User-Agent": USER_AGENT, "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            body = response.read().decode("utf-8", errors="replace")
            if response.status != 200:
                print(
                    f"Avertissement : {url.split('?')[0]} HTTP {response.status}",
                    file=sys.stderr,
                )
                return None
            return json.loads(body)
    except Exception as exc:  # réseau, DNS, TLS, JSON invalide…
        print(
            f"Avertissement : source indisponible ({url.split('?')[0]}) : {exc}",
            file=sys.stderr,
        )
        return None


# ── Webradios (Radio Browser) ────────────────────────────────────────────────
def radio_browser_stations(limit: int = 12, min_bitrate: int = 64) -> list[dict]:
    """
    Stations MP3 vérifiées par Radio Browser.

    On ne garde que les flux **HTTPS** (le schéma refuse le clair), en MP3
    (format lisible par le lecteur) et à un débit minimum, puis on déduplique
    par URL : le même flux est souvent listé plusieurs fois.
    """
    payload = _get_json(
        RADIO_BROWSER_ENDPOINT,
        {
            "limit": min(limit * 4, 100),
            "hidebroken": "true",
            "lastcheckok": "1",
            "order": "votes",
            "reverse": "true",
            "codec": "MP3",
        },
    )
    if not isinstance(payload, list):
        return []

    stations: list[dict] = []
    seen_urls: set[str] = set()
    per_host: dict[str, int] = {}
    for station in payload:
        if not isinstance(station, dict):
            continue
        url = str(station.get("url_resolved") or "").strip()
        name = str(station.get("name") or "").strip()
        station_id = str(station.get("stationuuid") or "").strip()
        if not name or not station_id:
            continue
        if not url.startswith("https://"):
            continue
        if str(station.get("codec") or "").upper() != "MP3":
            continue
        try:
            bitrate = int(station.get("bitrate") or 0)
        except (TypeError, ValueError):
            bitrate = 0
        if bitrate < min_bitrate:
            continue
        key = url.lower()
        if key in seen_urls:
            continue
        # Diversité : un même éditeur peut publier dix flux (WALM, laut.fm…).
        # On plafonne par hôte pour que la grille ne soit pas monopolisée.
        host = urllib.parse.urlsplit(url).hostname or ""
        if per_host.get(host, 0) >= 2:
            continue
        seen_urls.add(key)
        per_host[host] = per_host.get(host, 0) + 1

        country = str(station.get("country") or "").strip()
        tags = [t.strip() for t in str(station.get("tags") or "").split(",") if t.strip()]
        description = "Webradio en écoute libre"
        if country:
            description += f" ({country})"
        if tags:
            description += " · " + ", ".join(tags[:3])

        stations.append(
            {
                # Identifiant stable : dérivé de l'UUID Radio Browser.
                "id": "radio-rb-" + station_id.replace("-", "")[:10],
                "title": name[:120],
                "description": description[:300],
                "url": url,
                "bitrate": bitrate,
                "country": country,
                "tags": tags[:6],
            }
        )
        if len(stations) >= limit:
            break
    return stations


# ── Musique libre (Jamendo) ──────────────────────────────────────────────────
def jamendo_tracks(limit: int = 8) -> list[dict]:
    """
    Pistes Creative Commons de Jamendo.

    Nécessite une clé d'API gratuite dans l'environnement
    (`JAMENDO_CLIENT_ID`) : sans elle, la source est simplement ignorée.
    """
    client_id = os.environ.get("JAMENDO_CLIENT_ID", "").strip()
    if not client_id:
        return []

    payload = _get_json(
        JAMENDO_ENDPOINT,
        {
            "client_id": client_id,
            "format": "json",
            "limit": limit,
            "order": "popularity_total",
            "audioformat": "mp31",
            "include": "licenses",
            "imagesize": "500",
        },
    )
    results = (payload or {}).get("results") if isinstance(payload, dict) else None
    if not isinstance(results, list):
        return []

    tracks: list[dict] = []
    seen: set[str] = set()
    for track in results:
        if not isinstance(track, dict):
            continue
        url = str(track.get("audio") or "").strip()
        name = str(track.get("name") or "").strip()
        track_id = str(track.get("id") or "").strip()
        if not url.startswith("https://") or not name or not track_id:
            continue
        if track_id in seen:
            continue
        seen.add(track_id)
        artist = str(track.get("artist_name") or "").strip()
        tracks.append(
            {
                "id": "music-jamendo-" + track_id,
                "title": name[:120],
                "description": (f"{artist} — " if artist else "") + "Piste Creative Commons (Jamendo)",
                "url": url,
                "artist": artist,
                "thumbnail": str(track.get("image") or "").strip() or None,
                "license": _creative_commons_label(str(track.get("license_ccurl") or "")),
                "source": "Jamendo",
            }
        )
        if len(tracks) >= limit:
            break
    return tracks


# ── Musique libre (Internet Archive, collection « netlabels ») ───────────────
def internet_archive_audio(limit: int = 6, collection: str = "netlabels") -> list[dict]:
    """
    Pistes des netlabels hébergées par Internet Archive.

    Seuls les items portant une **URL de licence** sont retenus : c'est notre
    preuve de droits, et elle vient de la fiche elle-même, pas d'une
    supposition. Le fichier doit être un MP3 (format lisible partout).
    """
    payload = _get_json(
        IA_SEARCH_ENDPOINT,
        {
            "q": f"mediatype:audio AND collection:{collection} AND licenseurl:*",
            "fl[]": ["identifier", "title", "creator", "licenseurl"],
            "rows": max(limit * 3, 12),
            "page": 1,
            "output": "json",
        },
    )
    docs = ((payload or {}).get("response") or {}).get("docs")
    if not isinstance(docs, list):
        return []

    tracks: list[dict] = []
    seen: set[str] = set()
    for doc in docs:
        if not isinstance(doc, dict):
            continue
        identifier = str(doc.get("identifier") or "").strip()
        license_url = str(doc.get("licenseurl") or "").strip()
        if not identifier or not license_url.startswith("http"):
            continue
        if identifier in seen:
            continue
        seen.add(identifier)

        metadata = _get_json(IA_METADATA_ENDPOINT + identifier)
        files = (metadata or {}).get("files")
        if not isinstance(files, list):
            continue
        audio_file = _first_mp3(files)
        if not audio_file:
            continue

        title = _first_text(doc.get("title")) or identifier
        creator = _first_text(doc.get("creator"))
        tracks.append(
            {
                "id": "music-ia-" + identifier[:64],
                "title": title[:120],
                "description": (f"{creator} — " if creator else "")
                + "Netlabel libre (Internet Archive)",
                "url": "https://archive.org/download/"
                + urllib.parse.quote(identifier, safe="")
                + "/"
                + urllib.parse.quote(str(audio_file.get("name") or ""), safe=""),
                "artist": creator,
                "thumbnail": "https://archive.org/services/img/"
                + urllib.parse.quote(identifier, safe=""),
                "license": _creative_commons_label(license_url),
                "source": "Internet Archive",
            }
        )
        if len(tracks) >= limit:
            break
    return tracks


def _first_mp3(files: list) -> dict | None:
    """Choisit une piste MP3 non privée, en préférant le fichier d'origine."""
    candidates = [
        f for f in files
        if isinstance(f, dict)
        and str(f.get("name") or "").lower().endswith(".mp3")
        and str(f.get("private") or "").lower() != "true"
    ]
    if not candidates:
        return None
    candidates.sort(key=lambda f: 0 if str(f.get("source") or "") == "original" else 1)
    return candidates[0]


def _first_text(value) -> str:
    """Les métadonnées Internet Archive sont tantôt une chaîne, tantôt une liste."""
    if isinstance(value, list):
        value = value[0] if value else ""
    return str(value or "").strip()


def _creative_commons_label(cc_url: str) -> str:
    """Ramène une URL de licence CC vers un libellé court affichable."""
    value = cc_url.strip().lower()
    if not value:
        return "Creative Commons"
    if "publicdomain" in value or "zero" in value:
        return "CC0"
    if "by-nc-sa" in value:
        return "CC BY-NC-SA"
    if "by-nc" in value:
        return "CC BY-NC"
    if "by-sa" in value:
        return "CC BY-SA"
    if "by-nd" in value:
        return "CC BY-ND"
    return "CC BY"

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Point d'entrée local : synchronise catalogue et manifeste vers l'APK.

La logique vit dans `engines/_core/sync_asset.py` (versionnée, donc utilisable
par la CI) ; ce script n'est qu'un raccourci pratique :

    python tools/sync_app_asset.py
"""

from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "engines", "_core"))

from sync_asset import sync  # noqa: E402


if __name__ == "__main__":
    copied = sync()
    print(f"Assets Android synchronisés : {len(copied)} fichier(s) mis à jour.")

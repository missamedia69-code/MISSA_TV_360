#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Exécute tous les moteurs puis consolide site/data/catalog.json.

Usage : python engines/build_all.py
La CI GitHub Actions appelle ce script (voir .github/workflows/engines.yml).
"""

import os
import sys
import glob
import runpy

# Console Windows (cp1252) : on force UTF-8 pour les libellés accentués.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "_core"))
from schema import consolidate  # noqa: E402

HERE = os.path.dirname(__file__)


def main() -> int:
    engines = sorted(glob.glob(os.path.join(HERE, "*", "engine.py")))
    if not engines:
        print("Aucun moteur trouvé.", file=sys.stderr)
        return 1
    for path in engines:
        name = os.path.basename(os.path.dirname(path))
        print(f"\n══ Engine : {name} ══")
        runpy.run_path(path, run_name="__main__")
    print("\n══ Consolidation ══")
    consolidate()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

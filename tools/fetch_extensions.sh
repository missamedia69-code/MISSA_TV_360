#!/usr/bin/env bash
# Telecharge les .cs3 du depot phisher98 (branche builds) dans tools/cs3/
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p cs3
BASE="https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds"
curl -fsSL "$BASE/plugins.json" -o cs3/plugins.json
python - <<'PY'
import json, subprocess
for p in json.load(open("cs3/plugins.json")):
    name = p["internalName"]
    url = p["url"]
    subprocess.run(["curl", "-fsSL", "-m", "90", "-o", f"cs3/{name}.cs3", url], check=False)
    print("ok", name)
PY
ls cs3/*.cs3 | wc -l

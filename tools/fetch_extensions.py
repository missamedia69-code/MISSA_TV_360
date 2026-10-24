#!/usr/bin/env python3
import json, os, urllib.request

tools_dir = os.path.dirname(os.path.abspath(__file__))
cs3_dir = os.path.join(tools_dir, "cs3")
os.makedirs(cs3_dir, exist_ok=True)

plugins_json_url = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/plugins.json"
plugins_json_path = os.path.join(cs3_dir, "plugins.json")

print("Downloading plugins.json...")
urllib.request.urlretrieve(plugins_json_url, plugins_json_path)

with open(plugins_json_path, "r", encoding="utf-8") as f:
    plugins = json.load(f)

print(f"Found {len(plugins)} plugins. Downloading...")
count = 0
for p in plugins:
    name = p.get("internalName") or p.get("name")
    url = p.get("url")
    if name and url:
        dest = os.path.join(cs3_dir, f"{name}.cs3")
        try:
            urllib.request.urlretrieve(url, dest)
            count += 1
            print(f"Downloaded [{count}/{len(plugins)}]: {name}")
        except Exception as e:
            print(f"Failed to download {name}: {e}")

print(f"Downloaded {count} .cs3 files into {cs3_dir}")

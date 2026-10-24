#!/usr/bin/env python3
"""Audit de compatibilite : compare ce que les extensions .cs3 APPELLENT dans
com.lagradost.cloudstream3.* avec ce que l'APK de l'appli DEFINIT.

Usage : python3 audit_api.py <dossier_ou_fichiers_.cs3> --apk <app.apk> [--prefix com/lagradost/]
Sortie : liste des classes / methodes / champs manquants, classee par extension.
Code retour : 0 = rien ne manque, 1 = il manque quelque chose.
Prerequis : pip install --break-system-packages androguard
"""
import argparse, collections, glob, io, os, sys, zipfile
from loguru import logger
logger.remove()
from androguard.core.dex import DEX

def dex_blobs(path):
    """Renvoie la liste des contenus classes*.dex d'un .cs3 / .apk / .dex."""
    if path.endswith(".dex"):
        return [open(path, "rb").read()]
    out = []
    with zipfile.ZipFile(path) as z:
        for n in sorted(z.namelist()):
            if n.startswith("classes") and n.endswith(".dex"):
                out.append(z.read(n))
    return out

def proto_str(m):
    if hasattr(m, "get_descriptor"):          # methode DEFINIE (EncodedMethod)
        return m.get_descriptor().replace(" ", "")
    p = m.get_proto()               # ex. ['(Ljava/lang/String; I)', 'V'] selon la version
    if isinstance(p, (list, tuple)):
        return "".join(str(x) for x in p).replace(" ", "")
    return str(p).replace(" ", "")

def collect_defined(apk, prefix):
    classes = {}                    # classe -> dict(super, interfaces, methods, fields)
    for blob in dex_blobs(apk):
        d = DEX(blob)
        for c in d.get_classes():
            name = c.get_name()
            if not name.startswith("L" + prefix):
                continue
            info = classes.setdefault(name, dict(super=c.get_superclassname(),
                                                 ifaces=list(c.get_interfaces() or []),
                                                 methods=set(), fields=set()))
            for m in c.get_methods():
                info["methods"].add((m.get_name(), proto_str(m)))
            for f in c.get_fields():
                info["fields"].add(f.get_name())
    return classes

def collect_needed(cs3, prefix):
    needed = set()                  # ("M", classe, nom, proto) ou ("F", classe, nom, "")
    own = set()
    blobs = dex_blobs(cs3)
    for blob in blobs:
        d = DEX(blob)
        for c in d.get_classes():
            own.add(c.get_name())
    for blob in blobs:
        d = DEX(blob)
        mh = d.get_methods_id_item()
        items = getattr(mh, "methods", None) or getattr(mh, "method_id_items", None) or []
        for m in items:
            c = m.get_class_name()
            if c.startswith("L" + prefix) and c not in own:
                needed.add(("M", c, m.get_name(), proto_str(m)))
        fh = d.get_fields_id_item()
        fitems = getattr(fh, "elem", None) or getattr(fh, "field_id_items", None) or []
        for f in fitems:
            c = f.get_class_name()
            if c.startswith("L" + prefix) and c not in own:
                needed.add(("F", c, f.get_name(), ""))
    return needed

def resolves(defined, cls, kind, name, proto, seen=None):
    """Remonte la hierarchie (super-classes + interfaces) definie dans l'APK."""
    seen = seen or set()
    if cls in seen or cls not in defined:
        return False
    seen.add(cls)
    info = defined[cls]
    if kind == "M" and (name, proto) in info["methods"]:
        return True
    if kind == "F" and name in info["fields"]:
        return True
    for parent in [info["super"]] + info["ifaces"]:
        if parent and resolves(defined, parent, kind, name, proto, seen):
            return True
    return False

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("inputs", nargs="+")
    ap.add_argument("--apk", required=True)
    ap.add_argument("--prefix", default="com/lagradost/cloudstream3/")
    a = ap.parse_args()
    files = []
    for i in a.inputs:
        files += sorted(glob.glob(os.path.join(i, "*.cs3"))) if os.path.isdir(i) else [i]
    defined = collect_defined(a.apk, a.prefix)
    print(f"[audit] classes definies dans l'APK sous {a.prefix} : {len(defined)}")
    total_missing = 0
    global_missing = collections.defaultdict(set)
    for f in files:
        need = collect_needed(f, a.prefix)
        missing = []
        for kind, cls, name, proto in sorted(need):
            if not resolves(defined, cls, kind, name, proto):
                missing.append((kind, cls, name, proto))
        print(f"\n== {os.path.basename(f)} : {len(need)} references, {len(missing)} manquantes")
        for kind, cls, name, proto in missing:
            tag = "CLASSE ABSENTE" if cls not in defined else ("METHODE" if kind == "M" else "CHAMP")
            print(f"   [{tag}] {cls[1:-1].replace('/', '.')} {name}{proto}")
            global_missing[(kind, cls, name, proto)].add(os.path.basename(f))
        total_missing += len(missing)
    print(f"\n[audit] manquants distincts : {len(global_missing)}")
    sys.exit(1 if total_missing else 0)

if __name__ == "__main__":
    main()

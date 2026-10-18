# Moteurs de contenu — MISSA TV 360

Chaque catégorie de l'application possède son **moteur** (engine) : un script
Python qui produit un JSON de catalogue. Le dépôt GitHub agit comme le
« serveur » de l'application : la CI exécute tous les moteurs, puis GitHub
Pages sert les JSON. L'application Android les lit en HTTPS.

## Rôle d'un moteur

1. **Rassembler** les contenus d'une catégorie (fichiers locaux, sources
   libres/externes, manifestes).
2. **Convertir** en JSON conforme au schéma (`_core/schema.py`).
3. **Écrire** `site/data/<cat>.json` (+ `catalog.json` consolidé).

## Schéma

Voir `_core/schema.py`. Un `catalog.json` a cette forme :

```json
{
  "generated_at": "2026-10-18T12:00:00+00:00",
  "rows": [
    {
      "id": "films",
      "title": "Films",
      "items": [
        {
          "id": "film-phantom",
          "title": "Phantom of the Opera",
          "kind": "movie",
          "stream": { "url": "https://…/x.mp4", "format": "mp4", "live": false }
        }
      ]
    }
  ]
}
```

Pour les **jeux**, `kind` vaut `"game"` et l'item porte `download_url`,
`size_bytes`, `sha256`, `package_type`, `version_name`, `version_code`.

## Exécuter un moteur localement

```bash
python engines/jeux/engine.py          # écrit site/data/catalog.json
python engines/jeux/engine.py --hash chemin/fichier.apk   # génère le sha256
```

## Lancer tous les moteurs

```bash
for f in engines/*/engine.py; do python "$f"; done
```

## Ajouter un contenu

- **Jeux** : remplir `engines/jeux/jeux.yaml` (voir le commentaire dans ce
  fichier), publier l'APK en Release GitHub, renseigner `download_url` et
  `sha256`.
- **Autres catégories** : ajouter des entrées dans le manifeste/la source du
  moteur correspondant dans `engines/<cat>/`.

## Arborescence

```
engines/
  _core/schema.py     # modèle + validation + écriture (commun)
  jeux/engine.py      # moteur des jeux téléchargeables
  films/engine.py     # moteur des films
  series/engine.py    # moteur des séries
  … (un par catégorie)
site/data/            # JSON publiés (servis par Pages)
```

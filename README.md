# MISSA TV 360 — dépôt « serveur » + application Android TV

Ce dépôt contient **tout** ce qui fait vivre MISSA TV 360 : l'application
Android TV **et** le back-end de contenu, qui est ici-même. Pas de serveur
maison : les **moteurs** (engines) de ce dépôt produisent des JSON de
catalogue, **GitHub Pages** les sert en HTTPS, et l'application les consomme.

```
missa-tv360/
├── app/                      ← application Android TV (Kotlin + Leanback)
├── engines/                  ← les 10 moteurs de contenu (un par catégorie)
│   ├── _core/schema.py       # schéma commun + validation + écriture
│   ├── build_all.py          # exécute tous les moteurs + consolidation
│   ├── tv_direct/engine.py   # → site/data/tv_direct.json
│   ├── sport/engine.py       # → site/data/sport.json
│   ├── films/ … series/ … films_courts/ radio/ podcasts/
│   │   musique/ jeunesse/    # → un JSON par catégorie
│   └── jeux/engine.py        # → site/data/jeux.json (jeux téléchargeables)
├── site/data/                ← JSON publiés (servis par GitHub Pages)
│   ├── catalog.json          # agrégat des 10 lignes (source de l'app)
│   └── <cat>.json            # détail par catégorie
└── .github/workflows/
    └── engines.yml           ← CI : moteurs → Pages, à chaque push sur main
```

## Flux de contenu

1. Vous éditez la source d'un moteur (ex. `engines/jeux/jeux.yaml`).
2. Push sur `main` → la CI exécute `python engines/build_all.py`.
3. GitHub Pages publie `site/` en HTTPS.
4. L'app Android lit `<base>/data/catalog.json` au démarrage, avec repli sur
   son catalogue embarqué si le réseau est indisponible.

## Ordre des 10 catégories (imposé par l'app)

TV Direct · Sport Direct · Films · Séries · Films courts · Radio ·
Podcasts · Musique · Jeunesse · Jeux vidéo

Les `id` de row du dépôt doivent rester : `tv_direct`, `sport`, `films`,
`series`, `films_courts`, `radio`, `podcasts`, `musique`, `jeunesse`,
`jeux` — l'app trie selon cette liste canonique.

## Lancer les moteurs en local

```bash
python engines/build_all.py
```

Voir `engines/README.md` pour le schéma de données et comment ajouter un
contenu (films, flux, jeux APK…).

## Configuration de l'app

`app/build.gradle.kts` → `BFF_BASE_URL` doit pointer vers l'URL Pages du
dépôt, par exemple `https://<compte>.github.io/<depot>/`.

## Build de l'application

```bash
./gradlew :app:assembleDebug     # ou :app:assembleRelease signé
```

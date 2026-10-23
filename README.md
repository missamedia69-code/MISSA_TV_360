# MISSA TV 360

Application Android TV, mobile et tablette construite autour d’un catalogue JSON
unique. Le dépôt a été réinitialisé pour reconstruire les sources **pas à pas** :
aucun fournisseur, catalogue de démonstration ou contenu n’est préconfiguré.

## État actuel

- `sources/index.json` contient une liste `sources` vide ;
- l’application affiche donc un catalogue vide, sans inventer de contenu ;
- les anciens moteurs, adaptateurs de résolution, catalogues embarqués ou générés
  et publications automatiques ont été supprimés ;
- le lecteur, l’interface Leanback/mobile, la recherche locale, le cache,
  l’historique, la reprise, Ma liste, les jeux et les contrôles de sécurité sont
  conservés ;
- les extensions déjà installées restent prises en charge, mais aucun dépôt
  d’extensions n’est ajouté automatiquement.

## Architecture du catalogue

```text
sources/index.json
       │
       ├── chemin/vers/premier-fichier.json   (à créer plus tard)
       ├── chemin/vers/deuxieme-fichier.json  (à créer plus tard)
       └── …
              │
              ▼
     validation + fusion dans l’application
              │
              ▼
     un seul catalogue pour TV, mobile et tablette
```

L’application lit les fichiers directement depuis le répertoire `sources/` du
dépôt `missamedia69-code/MISSA_TV_360`, via HTTPS. Elle ne contacte plus d’API de
fournisseur pour compléter une fiche ou résoudre un flux au moment du clic.

Le chargeur se trouve dans :

- `data/json/JsonHttpClient.kt` : transport HTTPS borné ;
- `data/json/JsonSourceRepository.kt` : lecture, cache et gestion des erreurs ;
- `data/json/JsonCatalog.kt` : validation, conversion et fusion ;
- `data/CatalogRepository.kt` : façade utilisée par les écrans.

## 1. Déclarer les fichiers dans `sources/index.json`

État livré :

```json
{
  "schema_version": 1,
  "sources": []
}
```

Pour commencer une source, créer d’abord son JSON dans `sources/`, puis le
déclarer dans l’index :

```json
{
  "schema_version": 1,
  "sources": [
    {
      "id": "ma_source",
      "name": "Nom éditorial",
      "path": "ma_source.json",
      "enabled": true
    }
  ]
}
```

Règles de l’index :

- `schema_version` doit valoir `1` ;
- `id` est unique, en minuscules, avec uniquement `a-z`, `0-9`, `_` ou `-` ;
- `path` est un chemin relatif à `sources/`, se termine par `.json` et ne peut
  contenir ni `..`, ni URL, ni chemin absolu ;
- `enabled` est facultatif et vaut `true` par défaut ;
- `name` est facultatif et sert d’origine éditoriale si un item ne précise pas
  son champ `source`.

Tous les fichiers activés sont fusionnés. Les rangées ayant le même `id` sont
regroupées et les identifiants internes des items sont préfixés par l’`id` de la
source pour éviter les collisions.

## 2. Format d’un fichier de catalogue

Chaque fichier déclaré utilise ce conteneur :

```json
{
  "schema_version": 1,
  "rows": [
    {
      "id": "films",
      "title": "Films",
      "items": []
    }
  ]
}
```

Une rangée vidéo ou audio accepte des items avec les champs suivants :

| Champ | Règle |
|---|---|
| `id` | obligatoire, unique dans la source |
| `title` | obligatoire |
| `kind` | `live_tv`, `live_sport`, `movie`, `series`, `short`, `radio`, `podcast`, `music` ou `kids` |
| `stream.url` | obligatoire, HTTPS, directement lisible par le lecteur |
| `stream.format` | `mp4`, `hls` ou `mp3` ; déduit de l’URL si absent |
| `stream.live` | booléen facultatif pour un direct |
| `description` | facultatif |
| `thumbnail_url` | vignette HTTPS facultative |
| `series_title`, `season_number`, `episode_number` | métadonnées facultatives de série |
| `duration_sec`, `audio_only` | métadonnées facultatives de lecture |
| `tags`, `creator`, `release_year` | métadonnées facultatives |
| `source`, `license` | attribution et licence facultatives, affichées sans être inventées |

Un item invalide est ignoré ; il ne produit ni carte vide ni fausse fiche. Une
URL de page web, un identifiant de fournisseur ou un flux nécessitant une
résolution externe ne suffit pas : le JSON doit fournir le flux final.

## 3. Jeux

Les jeux utilisent une rangée dont l’`id` est `jeux` (ou `games`). En plus des
champs communs, un item de jeu exige :

- `kind: "game"` ;
- `download_url` en HTTPS ;
- `sha256` avec exactement 64 caractères hexadécimaux ;
- facultativement `size_bytes`, `package_type`, `version_name`,
  `version_code`, `genre` et `thumbnail_url`.

Le téléchargement reste limité en taille, les redirections sont revérifiées et
l’installation n’est proposée qu’après validation du SHA-256. Les hôtes de
paquets sont limités aux domaines GitHub explicitement autorisés dans
`GameDownloader`.

## Validation, erreurs et cache

- 64 fichiers au maximum dans l’index, 100 rangées par fichier et 500 items par
  rangée ;
- index limité à 256 Kio et chaque catalogue à 2 Mio ;
- délais de connexion et de lecture bornés ;
- une source en échec utilise son dernier JSON valide lorsqu’il existe ;
- un rafraîchissement partiel ne remplace pas un catalogue complet déjà mis en
  cache ;
- un index distant **valide et vide** efface réellement le catalogue ;
- hors ligne, les dernières données valides restent affichées si elles existent.

## Construire et tester

Prérequis : JDK 17 et Android SDK 35.

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
python3 -m unittest discover -s tests -p 'test_*.py'
python3 -m unittest discover -s extension-tools -p 'test_*.py'
```

Pour tester les JSON d’une branche sans changer l’URL stable :

```bash
./gradlew :app:assembleDebug \
  -PjsonSourceBaseUrl=https://raw.githubusercontent.com/missamedia69-code/MISSA_TV_360/refs/heads/NOM_DE_BRANCHE/sources/
```

L’URL doit pointer sur le répertoire qui contient `index.json` et utiliser HTTPS.
La CI compile et teste l’application ; elle ne publie ni release ni site.

## Extensions

Le moteur générique et les dépôts ajoutés manuellement sont documentés dans
[`docs/extensions.md`](docs/extensions.md). Ils sont indépendants de la
reconstruction des sources JSON et ne fournissent aucun contenu par défaut.

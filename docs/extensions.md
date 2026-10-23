# Extensions MISSA TV 360

Le moteur d’extensions est conservé comme une fonctionnalité générique, mais le
dépôt ne fournit **aucun dépôt, fournisseur ou contenu préconfiguré**. Au
démarrage, l’application recharge uniquement les extensions déjà installées.
L’utilisateur doit ajouter explicitement l’URL d’un dépôt depuis l’écran
Extensions.

## Format d’un dépôt

Un dépôt manuel expose un `repo.json` HTTPS :

```json
{
  "manifestVersion": 1,
  "name": "Nom du dépôt",
  "pluginLists": ["https://depot.example/plugins.json"]
}
```

Chaque liste contient des entrées d’extension avec notamment `url`,
`internalName`, `version`, `apiVersion`, `fileSize` et `fileHash`. L’archive
`.msa` est un ZIP contenant `manifest.json` et `classes.dex`.

- `fileHash` est obligatoire et utilise le format `sha256-<empreinte>` ;
- `apiVersion` doit correspondre à `plugin-api` ;
- `status = 0` désactive l’extension côté auteur ;
- les URL et les tailles de manifestes sont validées avant téléchargement.

## Installation et exécution

1. L’utilisateur ajoute le dépôt dans l’écran Extensions.
2. Le manifeste et les listes sont lus avec des limites de taille et de temps.
3. L’archive téléchargée est vérifiée par SHA-256 avant installation atomique.
4. Le chargeur isole le DEX et enregistre les providers déclarés.
5. Une extension en échec est désactivée sans empêcher le catalogue JSON de
   fonctionner.

Aucune extension n’est installée automatiquement au premier lancement.

## Outil de publication

`extension-tools/build_extension_repo.py` construit une `plugins.json` à partir
d’artefacts `.msa` et de leurs métadonnées. Il calcule la taille et l’empreinte
SHA-256 afin d’éviter une liste désynchronisée des archives publiées.

```bash
python3 extension-tools/build_extension_repo.py \
  --builds-dir extensions-builds \
  --base-url https://depot.example/extensions/builds \
  --out plugins.json
```

Le mode `--check` vérifie qu’un fichier existant correspond aux artefacts.
L’outil ne publie ni release ni site : ces opérations restent manuelles et
relèvent du dépôt d’extensions concerné.

## Tests

```bash
python3 -m unittest discover -s extension-tools -p 'test_*.py'
```

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
`internalName`, `version`, `apiVersion`, `fileSize` et, lorsqu’il est fourni,
`fileHash`. Les archives `.msa` natives et `.cs3` Cloudstream sont des ZIP
contenant `manifest.json` et `classes.dex`.

- `fileHash` est facultatif pour accepter les manifestes Cloudstream historiques ;
  lorsqu’il est présent, il utilise `sha256-<empreinte>` (ou la forme acceptée
  `sha256:<empreinte>`) et l’archive est vérifiée avant installation ;
- `apiVersion` doit correspondre à `plugin-api` pour une extension native ; les
  anciens manifestes Cloudstream sans cette valeur suivent le chemin de
  compatibilité dédié ;
- `status = 0` désactive l’extension côté auteur ;
- les URL et les tailles de manifestes sont validées avant téléchargement.

La boîte d’ajout accepte aussi les formats de partage Cloudstream saisis
explicitement : un code court tel que `cloudstreamrepo`, un code `!code`, ou un
lien `cloudstreamrepo://…` / `https://cs.repo/?…`. Un code court n’est pas un
nom de domaine : sa redirection est résolue puis sa cible est validée avant la
lecture du manifeste. Aucun de ces codes n’est préconfiguré ni ajouté au
démarrage.

## Installation et exécution

1. L’utilisateur ajoute le dépôt dans l’écran Extensions.
2. Le manifeste et les listes sont lus avec des limites de taille et de temps.
3. Si une empreinte est déclarée, l’archive téléchargée est vérifiée par SHA-256
   avant installation atomique ; les tailles et la structure du paquet sont
   contrôlées dans tous les cas.
4. Le chargeur isole le DEX et enregistre les providers déclarés.
5. Si le chargement échoue, le fichier et l’enregistrement installé sont purgés,
   l’échec est conservé séparément et la même version n’est pas réinstallée
   automatiquement au démarrage.
6. Une extension en échec n’empêche pas le catalogue JSON ni les autres
   extensions de fonctionner.

Aucune extension n’est installée automatiquement au premier lancement. Les
états et les actions de l’écran Extensions distinguent installation, désactivation,
incompatibilité, mise à jour et nouvelle tentative manuelle.

L’état **Chargée** signifie uniquement que le DEX et son provider ont été
enregistrés. Il ne garantit ni réponse réseau ni rangée de contenu. Les
providers sont interrogés en parallèle avec un délai individuel. Sur l’accueil
TV, l’action Extensions reste fixe et accessible au D-pad ; en l’absence de
rangées, un état explicite distingue chargement, échec et provider sans contenu,
sans créer de carte de remplacement.

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

## Compatibilité Cloudstream expérimentale

Le prototype `cloudstream-compat` est limité à la surface inspectée du plugin
BuzzMonclick. Il ne signifie pas que tous les `.cs3` sont compatibles et ne
fournit pas de contenu préconfiguré. La provenance de la fixture, les règles de
non-réinvention des licences et le résultat réel de la sonde P3 sont documentés
dans [`cloudstream-compat.md`](cloudstream-compat.md).

La porte P3 reste actuellement ouverte : le plugin se charge, mais son domaine
ne produit aucune rangée réelle sur l’émulateur. P4 n’est pas lancé sur cette
base.

## Tests

```bash
python3 -m unittest discover -s extension-tools -p 'test_*.py'
./gradlew :app:testDebugUnitTest :cloudstream-compat:testDebugUnitTest
./gradlew :app:assembleDebugAndroidTest
```

Les tests unitaires et la compilation ne remplacent pas la validation sur
appareil/émulateur, notamment pour le réseau, le D-pad, la lecture et le
redémarrage.

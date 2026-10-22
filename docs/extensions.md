# Extensions MISSA TV 360 — architecture et exploitation

Ce document décrit le moteur d'extensions ajouté à l'application : dépôts,
installation, chargement du code, publication et limites de sécurité.

---

## 1. Vue d'ensemble

Trois niveaux, identiques au modèle des écosystèmes d'extensions Android :

```
repo.json                    (pointeur stable, que l'utilisateur colle)
   └── pluginLists[] ─────► plugins.json        (liste des extensions publiées)
                               └── url ─────►  <internalName>.msa
                                                (ZIP : manifest.json + classes.dex)
```

| Fichier | Rôle | Où il vit |
|---|---|---|
| `site/extensions/repo.json` | nom, description, `manifestVersion`, liste de `pluginLists` | versionné, publié par GitHub Pages |
| `plugins.json` | tableau d'extensions (`url`, `internalName`, `version`, `fileHash`…) | **généré par la CI**, asset de la release `extensions` |
| `*.msa` | archive ZIP : `manifest.json` + `classes.dex` | asset de la même release |

Pourquoi la release plutôt qu'un fichier versionné : le champ `fileHash` doit
correspondre **octet pour octet** à l'archive réellement produite par Gradle.
Un `plugins.json` commité à la main se désynchronise au premier rebuild, et
l'application refuserait alors l'installation (hash invalide). La CI calcule
donc les deux ensemble et les publie ensemble :

```
https://github.com/missamedia69-code/MISSA_TV_360/releases/download/extensions/plugins.json
https://github.com/missamedia69-code/MISSA_TV_360/releases/download/extensions/hello-provider.msa
```

Le tag `extensions` est **stable** : chaque build vert remplace les assets
(`--clobber`) et déplace le tag sur le commit courant, donc l'URL ne change
jamais côté application.

L'application ne connaît **qu'une seule URL** par défaut :
`BuildConfig.EXTENSION_REPO_URL`, construite à partir de `bffBaseUrl` :

```
https://missamedia69-code.github.io/MISSA_TV_360/extensions/repo.json
```

Surchargeable au build :

```bash
./gradlew assembleDebug -PextensionRepoUrl=https://mon-serveur/extensions/repo.json
```

---

## 2. Parcours au premier démarrage

1. `MainActivity` → `BrowseFragment` affiche immédiatement le catalogue embarqué.
2. `BrowseFragment.loadExtensions()` (non bloquant) :
   1. `ExtensionManager.init(context)` ;
   2. `ExtensionManager.bootstrapIfNeeded()` **sur le dispatcher IO** :
      - si `bootstrap_done` est faux et que le nombre de tentatives est < 5 :
        - ajoute le dépôt intégré ;
        - lit `repo.json` puis chaque `pluginLists` (max 10) ;
        - télécharge au plus 16 extensions (`status != 0`), vérifie le SHA-256,
          installe de façon atomique, charge le DEX ;
        - pose `bootstrap_done = true` **uniquement si `repo.json` a été lu** :
          un premier démarrage hors ligne sera retenté ;
   3. `ExtensionCatalog.rows()` interroge les providers chargés (timeout 8 s
      chacun) et renvoie des `VideoRow` ;
   4. `BrowseFragment.appendRows()` ajoute ces rangées à la suite du catalogue,
      puis ajoute la carte « Extensions ».
3. Les démarrages suivants : `loadInstalled()` recharge les `.msa` déjà
   présents ; `refresh()` applique les mises à jour et le *kill switch*.

Une extension **échouée** n'empêche jamais l'affichage : elle est journalisée,
désactivée, et les autres continuent.

---

## 3. Format d'une extension

### 3.1 `plugins.json` (un élément)

```json
{
  "url": "https://…/extensions/builds/hello-provider.msa",
  "name": "Démo — films libres",
  "internalName": "hello-provider",
  "version": 1,
  "status": 1,
  "apiVersion": 1,
  "authors": ["MISSA TV 360"],
  "description": "…",
  "language": "fr",
  "tvTypes": ["Movies"],
  "fileSize": 48123,
  "fileHash": "sha256-9f2c…"
}
```

* `status` : `0` désactivée par l'auteur (kill switch), `1` ok, `2` lente,
  `3` bêta.
* `fileHash` : **obligatoire**. Une extension sans empreinte est refusée à
  l'installation.
* `apiVersion` : doit être égal à `PLUGIN_API_VERSION` (`plugin-api`).

### 3.2 `manifest.json` (dans l'archive)

```json
{
  "name": "Démo — films libres",
  "version": 1,
  "apiVersion": 1,
  "pluginClassName": "com.missa.extensions.hello.HelloPlugin",
  "requiresResources": false
}
```

### 3.3 Code

Une extension étend `MissaPlugin` et enregistre un ou plusieurs `Provider` :

```kotlin
class HelloPlugin : MissaPlugin() {
    override fun load() {
        registerProvider(HelloProvider())
    }
}

class HelloProvider : Provider {
    override val id = "hello-provider"
    override val name = "Démo — films libres"
    override suspend fun rows(): List<Row> = listOf(
        Row("blender", "Films Blender", listOf(
            Item(
                id = "bbb",
                title = "Big Buck Bunny",
                kind = Kind.MOVIE,
                stream = Stream(url = "https://…/BigBuckBunny.mp4", format = "mp4"),
                source = "Blender Foundation (miroir Google)",
                license = "CC BY 3.0",
            )
        ))
    )
}
```

`rows()` est `suspend` : l'acquisition réseau est autorisée. Les items sans
`stream.url` sont ignorés par l'application (pas de carte menant à un lecteur
vide).

---

## 4. Construire et publier une extension

```bash
# 1. Archive .msa + métadonnées (nécessite JDK 17 et le SDK Android pour d8)
./gradlew :extensions-src:hello-provider:packagePlugin

# 2. Générer la liste publiée (calcule sha256 + taille)
mkdir -p /tmp/builds
cp extensions-src/hello-provider/build/plugin/hello-provider.msa /tmp/builds/
python3 extension-tools/build_extension_repo.py \
  --builds-dir /tmp/builds \
  --base-url https://github.com/missamedia69-code/MISSA_TV_360/releases/download/extensions \
  --out /tmp/plugins.json
```

Le workflow `.github/workflows/extensions.yml` fait tout cela : tests, jar du
contrat plugin, `packagePlugin`, génération de `plugins.json`, puis — sur un
push uniquement — création/mise à jour de la release `extensions` et
téléversement des assets (`*.msa` + `plugins.json`, `--clobber`).

```bash
# publier à la demande (après un changement d'extension)
gh workflow run extensions.yml --ref main
gh release view extensions --json assets \
  -q '.assets[] | "\(.name)\t\(.size) o\t\(.updatedAt)"'
```

Rien n'est écrit dans le dépôt : ni binaire, ni `plugins.json`, aucun commit
automatique. L'option `--check` de l'outil reste disponible pour qui préfère
héberger la liste statiquement (GitHub Pages).

---

## 5. Ajouter un dépôt dans l'application

Écran **Extensions** (carte « Chaînes & extensions » en fin d'accueil) :

* **Ajouter un dépôt** : colle une URL `https://…/repo.json` (pré-remplie
  depuis le presse-papier si elle y ressemble) ou le format de partage
  `Nom : https://…/repo.json` ;
* **Actualiser** : relit les dépôts, applique les mises à jour et le kill switch ;
* **Clic sur une extension** : installer / mettre à jour ;
* **Appui long** : supprimer l'extension ou le dépôt (les fichiers et les
  providers sont retirés).

---

## 6. Sécurité — ce qui est protégé, et ce qui ne l'est pas

### Garanties implémentées

| Risque | Mesure |
|---|---|
| Exécution de code non vérifié | `fileHash` obligatoire, comparaison SHA-256 à temps constant |
| Artefact corrompu / partiel | téléchargement en mémoire → écriture temporaire → `renameTo` atomique |
| Redirection vers HTTP | redirections suivies manuellement, chaque saut revalidé ; HTTPS exigé |
| Dépôt géant / DoS | `repo.json` ≤ 64 Ko, liste ≤ 512 Ko, artefact ≤ 8 Mio, ≤ 10 listes, ≤ 16 installations automatiques |
| Plugin compilé pour une autre API | `apiVersion` vérifié avant chargement |
| Extension cassée qui bloque l'accueil | timeout 8 s par provider, exceptions isolées |
| Extension retirée par son auteur | `status = 0` → déchargée au prochain rafraîchissement |
| Chemin de fichier piégé | nom de fichier dérivé de `internalName` (caractères filtrés) + dossier = hash de l'URL du dépôt |

### Ce qui n'est PAS protégé (assumé, à documenter dans toute distribution)

* **Le code de l'extension s'exécute avec les permissions de l'application**
  (aucun bac à sable, aucune signature). Un dépôt compromis = code arbitraire.
  Ne jamais ajouter un dépôt dont on ne contrôle pas la publication.
* Il n'existe **pas de signature cryptographique** des auteurs : seul le hash
  publié dans `plugins.json` protège contre une altération en transit, pas
  contre un dépôt malveillant qui publierait son propre hash.
* Aucune analyse statique n'est faite sur le DEX.
* Sur un build `release`, R8 conserve le contrat (`proguard-rules.pro`) mais
  obfusque le reste : une extension qui référencerait des classes internes de
  l'application casserait. Seul `plugin-api` est un contrat public.

Recommandation : réserver l'ajout de dépôts à des URLs HTTPS du projet, et
n'activer la fonction que sur des builds dont la source est maîtrisée.

---

## 7. Désactivation d'urgence (kill switch)

Pour retirer une extension cassée sans publier d'APK :

1. passer son `status` à `0` dans `plugins.json` (ou retirer son entrée) ;
2. régénérer / committer ;
3. au prochain `refresh()`, l'application la décharge immédiatement ;
4. l'utilisateur peut aussi la supprimer depuis l'écran Extensions.

Un `version` supérieur déclenche la mise à jour automatique (si
`auto_update` est actif, valeur par défaut).

---

## 8. État de vérification

* `extension-tools/test_build_extension_repo.py` : **15 tests, tous verts**
  (`python3 -m unittest discover -s extension-tools`).
* Le code Kotlin (moteur + écran + extension d'exemple) **n'a pas été compilé
  dans l'environnement de rédaction** : ni JDK, ni SDK Android, ni Gradle n'y
  sont installés. Il doit être validé par :

  ```bash
  ./gradlew :app:assembleDebug :app:testDebugUnitTest
  ./gradlew :extensions-src:hello-provider:packagePlugin
  ```

* La première publication est faite par la CI : au 2026-10-22, la chaîne
  `repo.json` → `plugins.json` → `hello-provider.msa` est servie depuis la
  release `extensions`, et `site/extensions/repo.json` (Pages) ne devient
  accessible qu'après fusion sur `main`.
* Tant que `repo.json` n'est pas servi (Pages pas encore déployé), le
  bootstrap échoue proprement : aucune exception visible, message d'état
  « dépôt injoignable », 5 tentatives maximum, puis abandon silencieux — c'est
  le comportement attendu, pas un bug.

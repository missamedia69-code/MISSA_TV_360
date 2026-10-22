# Refonte UX/UI — traçabilité et validation

Date de revue : 22 octobre 2026  
Branche : `arena/f1462e27-missa-tv-360`  
Référence fonctionnelle : `MISSA_TV_360_INSTRUCTIONS_REFONTE_COMPLETE_A_Z.txt`, phases 0–11.

> Ce document distingue les contrôles réellement exécutés des vérifications qui nécessitent un appareil Android. L’audit détaillé initial est dans `audit-ui-ux.md` ; il décrit la baseline, pas l’état actuel.

## 1. Lots livrés

| Phase | Réalisation dans le code | État de validation |
|---|---|---|
| 0 — Baseline et sécurité | Inventaire de l’application native Kotlin/Leanback/Media3, repositories, catalogue, jeux et extensions ; branche dédiée ; aucune suppression des sources ni des données réelles. | Audit et baseline Python effectués. Build local impossible sans JDK. |
| 1 — Design system | Palette bleu nuit/bleu-gris/jaune conservée et centralisée ; styles `MissaText`, boutons, focus, badges, panneaux d’état, dimensions et contrastes. | XML et contrastes contrôlés par tests statiques. |
| 2 — Cartes et images | Un seul titre visible par carte ; ratios portrait/paysage/carré ; focus et contenu accessible ; vignettes avec fallback, cache mémoire/disque borné, décodage sous-échantillonné et limite de taille. | Compilation CI verte ; tests de ressources verts ; rendu réel non exécuté. |
| 3 — Accueil TV | Catalogue partagé, rangées réelles Reprendre/Ma liste, cartes Tout voir, en-têtes liés au presenter Leanback, actualisation sans reconstruction systématique des rangées. | Compilation et tests unitaires CI verts ; D-pad non testé sur appareil. |
| 4 — Recherche/catégories | Recherche locale + moteur Films, annulation et requêtes obsolètes ignorées, timeout réseau, erreur/réessayer, effacement mobile, grilles adaptatives et états vides. | Chemins compilés ; recherche tactile/D-pad à vérifier manuellement. |
| 5 — Fiches | Hiérarchie titre/métadonnées/description, actions Lecture-Reprendre/Ma liste/Signaler, source/licence disponibles, similaires locaux ou distants, variants compact et large. | Compilation CI verte ; retour et restauration du focus à vérifier sur appareil. |
| 6 — Lecteur | Media3 conservé ; reprise stockée, pause/reprise et contrôles natifs, retry borné par action utilisateur, erreurs réseau/format/source distinctes, retour sans fermeture automatique sur erreur, libération au pause. | Compilation CI verte ; aucune lecture réelle ou rotation n’a été testée. |
| 7 — Mobile | Hôte tactile distinct de Leanback, accueil/catégories/recherche/fiches/jeux réutilisant les mêmes stores, cartes tactiles, bouton d’effacement, navigation retour et insets système. | Variants compilés ; portrait/paysage et clavier non testés sur appareil. |
| 8 — Tablette | Variants `layout-sw600dp`, fiche en deux colonnes, grilles à colonnes variables et jeux en deux colonnes sur largeur étendue. | Contrat des variants contrôlé statiquement ; redimensionnement réel non testé. |
| 9 — États/cache/jeux/extensions | Cache et asset local avant le réseau, statuts hors ligne/stale honnêtes, retry, progression et liste persistantes ; téléchargements SHA-256, annulation, erreurs, permission d’installation ; dépôts/extensions existants conservés. | Tests métier et compilation CI ; aucun téléchargement ni installation système exécuté. |
| 10 — Accessibilité/performance | Cibles tactiles de 48 dp, descriptions accessibles, contraste vérifié, animations courtes respectant le réglage système, images bornées, coroutines IO et annulation. | Contrôles statiques et revue de code ; TalkBack, police maximale et fluidité non mesurés. |
| 11 — Livraison | Commits petits sur la branche de travail, CI de compilation/tests avec marqueur `[no-release]`, tests métier et ressources ajoutés. | Le dernier run du lot commité est indiqué ci-dessous ; aucune release ni publication n’a été faite. |

## 2. Commandes et résultats exacts

### Local

- `bash ./gradlew --no-daemon :app:assembleDebug` : **impossible à lancer** dans le sandbox local. Erreur exacte : `ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.` Aucun JDK/SDK/émulateur local n’est disponible.
- `python3 -m unittest discover -s tests -v` après l’ajout des contrôles de ressources : **11 tests exécutés, 8 réussites, 3 échecs**. Les 3 échecs sont ceux de la baseline initiale :
  - deux tests attendent une vignette Archive alors que le moteur utilise son repli `placehold.co` lorsque la vignette n’est pas accessible ;
  - un test attend une seule source Films alors que le manifeste actuel en déclare deux (`internet_archive` et `dailymotion`).
  Les autres tests, dont les nouveaux contrôles XML/layout/contraste/toucher, passent. Ces tests n’ont pas été modifiés pour masquer les échecs.
- `git diff --check` : utilisé après les lots ; aucune erreur d’espaces signalée.
- Scanner XML local : ressources bien formées et doublons de valeurs recherchés ; les erreurs de doublon et de parent de style détectées en CI ont été corrigées.

### CI GitHub Actions

Le workflow exécute la compilation debug, `:app:testDebugUnitTest` et l’upload de l’APK de validation. Les étapes de release et de preview sont protégées par `[no-release]` et ont été sautées.

- Run `39848328028` : **échec**, `mergeDebugResources`, doublon `mobile_extensions` ; corrigé.
- Run `39848829525` : **échec**, `processDebugResources`, parent `style/MissaText` absent ; corrigé.
- Run `39849079645` : **succès** — Build debug APK, Run Android unit tests, Upload debug APK ; release/preview sautés.
- Run `39849536238` : **succès** — compilation et tests du lot de durcissement du catalogue/recherche et du presenter d’en-têtes.
- Run `39849777990` : **succès** — compilation, tests unitaires Android et upload du commit `f7e8209` ; release/preview sautés.

Les exécutions CI ne constituent pas des tests visuels, tactiles, audio, D-pad ou de lecture sur appareil.

## 3. Fonctions et données préservées

- Catalogue réel, cache et asset embarqué ; repositories Pages/BFF et moteur Films.
- Leanback et la navigation TV ; aucune dépendance graphique lourde ajoutée.
- Recherche, catégories, fiches, résolution des sources et en-têtes HTTP nécessaires au lecteur.
- Media3, HLS/MP4/MP3 selon les données, progression et reprise.
- Ma liste, historique de progression existant, signalements locaux.
- Jeux et extensions, y compris vérification SHA-256, stockage privé, FileProvider et confirmation Android d’installation.
- Les données de catalogue et les URL configurées n’ont pas été remplacées par un catalogue de démonstration. Les métadonnées absentes restent absentes ; les placeholders sont filtrés des rangées de contenus.

## 4. Vérifications manuelles restant à faire

Aucun appareil, émulateur, ADB ou navigateur ne remplace ici un test de l’application Android native. Il reste donc à exécuter sur TV, téléphone et tablette :

1. D-pad complet : accueil, en-têtes, Tout voir, recherche, fiche, similaires, jeux, extensions, retour et restauration du focus.
2. Lecture d’un flux réellement disponible : pause/reprise, ±10 s, retour, reprise stockée, erreur réseau/source/format et retry.
3. Mobile : portrait/paysage, clavier de recherche, effacement, longs titres/descriptions, barres système et retour Android.
4. Tablette : rotation, redimensionnement, colonnes et fiche large/compacte.
5. Hors ligne/rétablissement réseau, images absentes, police maximale, TalkBack et contraste sur images claires.
6. Jeux : annulation pendant un téléchargement lent, échec/checksum, permission d’installation et ouverture du paquet vérifié.

Ces points ne sont pas déclarés validés tant qu’ils n’ont pas été exécutés sur un environnement Android réel.

## 5. Limites connues

- Le sandbox ne fournit pas de JDK/SDK/ADB/émulateur local ; la validation Android repose sur GitHub Actions.
- La disponibilité des flux, affiches et dépôts dépend de leurs services distants ; l’application conserve les contenus locaux et signale les échecs sans inventer de disponibilité.
- La baseline Python conserve trois attentes incompatibles avec le comportement/manifeste actuel ; elles sont documentées ci-dessus et restent à traiter dans un lot séparé si leur contrat doit être modernisé.
- Aucun test de performance mesuré (mémoire, temps de premier contenu, fluidité) ni test d’accessibilité assistée n’a été réalisé.

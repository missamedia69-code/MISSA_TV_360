# Audit UI/UX complet — MISSA TV 360

**Date :** 22 octobre 2026  
**Révision auditée :** `5039f6eb5b499fc62a4df7ba3cd1fa3f25f5f368`  
**Produit :** application Android TV native, Kotlin + Leanback + Media3, avec entrée secondaire dans le lanceur tactile Android.  
**Périmètre :** accueil, navigation, grille de catégorie, recherche, fiche détail, lecteur vidéo/audio, jeux, extensions, accessibilité, responsive/tactile, contenu, états UI et performance perçue.

> **Nature de l’audit.** Il s’agit d’une revue statique approfondie du code, des ressources et des données, complétée par l’inspection des visuels et des calculs de contraste WCAG. L’environnement ne contient ni JDK, ni émulateur ou appareil Android : aucun rendu ou flux n’a été exécuté. Les problèmes déterministes sont indiqués comme **confirmés** ; les points dépendants du rendu ou d’une télécommande sont explicitement marqués **à valider sur appareil**. Ce document n’est donc ni un test utilisateur ni une certification du rendu TV.
>
> **Chemins utilisés.** Les références `ui/`, `data/`, `games/`, `extensions/` et `playback/` sont relatives à `app/src/main/java/com/missa/tv360/` ; les références `res/` sont relatives à `app/src/main/`.

---

## 1. Synthèse dirigeante

MISSA TV 360 possède une **base visuelle cohérente et reconnaissable** : univers bleu nuit, accent jaune, cartes différenciées par format, focus généralement visible, repli graphique local et structure Leanback adaptée au principe de la télécommande. Les données livrées sont propres sur plusieurs points : 39 contenus, aucun identifiant dupliqué, aucune description vide, source et licence renseignées partout.

En revanche, plusieurs fonctions visibles ne forment pas encore une **boucle utilisateur complète et fiable** :

- la carte **« Tout voir » placée dans la grille ouvre une fiche sans flux**, puis un lecteur qui échoue ;
- **« Ma liste » peut être alimentée mais jamais consultée** ;
- les six morceaux de musique marqués `audio_only` sont traités comme de la vidéo ;
- hors ligne, l’application affiche le catalogue de démonstration au lieu du catalogue embarqué ou du dernier cache ;
- une erreur de lecture ferme immédiatement le lecteur, sans bouton Réessayer ;
- les installations/suppressions d’extensions ne rafraîchissent pas l’accueil déjà ouvert ;
- plusieurs contrôles de la fiche détail n’ont pas d’état de focus visible ;
- les téléchargements de jeux n’affichent ni progression, ni annulation, ni état installé.

La priorité n’est donc pas d’ajouter davantage de décoration. Elle est de **fiabiliser les parcours, rendre les états visibles et honnêtes, puis améliorer le contenu et la richesse des métadonnées**.

### Évaluation par parcours

| Parcours | État constaté | Principal motif |
|---|---|---|
| Accueil → contenu → détail | **Partiellement fonctionnel** | Bonne base Leanback, mais catalogue et rangées peuvent devenir incohérents |
| Accueil → Tout voir → contenu | **À corriger avant diffusion** | Carte « Tout voir » récursive et sans flux dans la grille |
| Recherche → résolution → lecture | **Fragile** | Recherche limitée aux films, peu d’états intermédiaires et erreurs peu actionnables |
| Ma liste | **Incomplet** | Ajout/retrait local sans vue de la liste |
| Reprendre | **Incohérent** | Filtre de progression non appliqué à la rangée et rafraîchissement absent |
| Lecture vidéo | **Minimaliste** | Lecture Media3 présente, récupération d’erreur absente |
| Radio / podcasts / musique | **Inégal** | Musique non reconnue comme audio ; pas de lecture en arrière-plan |
| Jeux | **Incomplet** | Téléchargement sans progression ni état installé/ouvrir |
| Extensions | **Utilisable mais peu découvrable** | Actions destructives cachées en appui long et accueil non rafraîchi |
| Accessibilité TV | **Partielle** | Bon focus des cartes, mais lacunes TalkBack, contrastes et focus secondaires |
| Usage tactile annoncé dans le manifeste | **Non abouti** | Layouts fixes, paysage forcé, petites cibles et aucune ressource alternative |

---

## 2. Ce qui fonctionne bien et doit être conservé

1. **Identité visuelle cohérente.** Le couple navy/jaune est appliqué aux cartes, boutons, badges et écrans de gestion (`res/values/colors.xml`).
2. **Focus des cartes lisible dans son principe.** Anneau jaune, zoom léger de 1,05 et changement de couleur du titre offrent trois signaux (`ui/MissaCardPresenter.kt:36-58`). L’absence d’ombre lourde est adaptée à une interface TV fluide.
3. **Trois familles de cartes.** Paysage, affiche et carré évitent une grille visuellement uniforme qui dénaturerait tous les contenus (`ui/CardShape.kt`).
4. **Focus initial pertinent sur la fiche.** Le bouton principal reçoit le focus à l’ouverture (`ui/DetailsFragment.kt:78-81`).
5. **Retour arrière préservé.** Les fragments de détail et de grille utilisent la back stack plutôt que de reconstruire systématiquement l’accueil.
6. **Repli graphique immédiat.** Les cartes et fiches ne dépendent pas uniquement du succès des images distantes (`ui/ThumbnailLoader.kt`, `ui/FallbackArtwork.kt`).
7. **Tolérance aux pannes des sources.** Plusieurs erreurs réseau sont isolées pour éviter un crash global ; les extensions en échec ne bloquent pas les autres fournisseurs.
8. **Métadonnées de provenance.** Les 39 éléments du catalogue embarqué ont une description, une source et une licence ; aucun ID n’est dupliqué.
9. **Sécurité des jeux.** SHA-256 obligatoire, limitation de taille et liste d’hôtes autorisés sont de bons garde-fous (`games/GameDownloader.kt`).
10. **Prise en charge native de la recherche TV.** Le clavier Leanback et l’entrée vocale sont une meilleure base qu’un clavier personnalisé incomplet.

Ces acquis doivent être conservés pendant les corrections plutôt que remplacés par une refonte visuelle globale.

---

## 3. Priorités critiques et majeures

| ID | Priorité | Constat confirmé | Conséquence UX | Correction recommandée |
|---|---|---|---|---|
| **P1-01** | Haute | La grille ajoute une carte `moreTargetRowId`, mais son clic envoie tous les objets vers `DetailsFragment` (`ui/CategoryGridFragment.kt:63-80,113-120`). | « Tout voir » ouvre une fausse fiche sans flux ; « Lire » aboutit à l’erreur générique puis à la fermeture du lecteur. | Supprimer cette carte dans la grille ou gérer explicitement son action. Ne jamais afficher une action de lecture si `streamUrl` est vide et qu’aucune résolution n’est possible. |
| **P1-02** | Haute | `audio_only` sert seulement à choisir le format MP3 ; il n’est pas conservé dans `Video`. `isAudioOnly` ne reconnaît que Radio et Podcast (`data/remote/PagesCatalogDto.kt:47,69-82`, `data/Video.kt:27,115`). | Les 6 morceaux de musique ont « Lire » au lieu d’« Écouter » et ne reçoivent pas le visuel audio du lecteur : risque d’écran noir. | Ajouter une propriété audio explicite au modèle et la propager depuis Pages, BFF et extensions. Aligner libellé, pochette, contrôles et session média. |
| **P1-03** | Haute | Le premier affichage appelle toujours `localCatalog()` ; si l’appareil est hors ligne, `loadCatalog()` n’est jamais appelé (`ui/BrowseFragment.kt:125-155`). | Le cache et le vrai asset `assets/catalog.json` ne sont pas utilisés au lancement hors ligne. L’utilisateur voit des flux de démonstration et perd des catégories réelles. | Initialiser avec le dernier cache, puis l’asset embarqué, puis seulement la démonstration. Afficher clairement « Hors ligne · catalogue du [date] » et proposer Réessayer. |
| **P1-04** | Haute | `MyListStore` n’est utilisé que dans la fiche détail ; aucune rangée, grille ou destination « Ma liste » n’existe. | L’utilisateur mémorise un contenu sans pouvoir le retrouver par cette fonction. | Ajouter une destination Ma liste, avec état vide, suppression et persistance. À défaut, retirer temporairement l’action. |
| **P1-05** | Haute | Toute erreur Media3 affiche un Toast puis appelle `finish()` (`playback/PlayerActivity.kt:158-162`). | Une coupure temporaire devient une sortie de lecture. Le message suggère de réessayer mais ne fournit aucune action directe. | Écran d’erreur intégré avec **Réessayer** focalisé et **Retour**, reconnexion bornée, distinction réseau/source/format et conservation de la position. |
| **P1-06** | Haute | `details_back` et `details_play` sont focalisables mais sans sélecteur ni listener de focus (`res/layout/fragment_details.xml:49-64,119-130`). | À la télécommande, le focus peut devenir invisible sur Retour ou sur le bouton lecture de la pochette. | Appliquer le même langage de focus qu’aux autres actions et vérifier tout le parcours D-pad. |
| **P1-07** | Haute | Après installation, un commentaire annonce une relecture du catalogue au retour, mais aucune n’est implémentée (`extensions/ExtensionsActivity.kt:107-114`). | Les nouvelles rangées n’apparaissent pas sur l’accueil déjà empilé ; les rangées supprimées peuvent y rester. Redémarrage nécessaire sans être indiqué. | Émettre un résultat vers Main/Browse, puis recharger uniquement les rangées d’extensions en conservant le focus. |
| **P1-08** | Haute | Le téléchargement expose un `StateFlow` de progression, mais `GamesFragment` ne le collecte jamais (`games/GameDownloader.kt:48-60`, `games/GamesFragment.kt:67-77`). | Jusqu’à 500 Mo peuvent être téléchargés avec seulement deux Toasts : pas de pourcentage, annulation ou prévention des clics répétés. | Barre de progression par jeu, état En cours, Annuler, Reprendre/Réessayer, erreur persistante et verrouillage contre les téléchargements concurrents du même jeu. |

---

## 4. Revue détaillée par écran et parcours

### 4.1 Accueil et architecture de navigation

#### A. La rangée « À la une » n’est pas un véritable hero — priorité moyenne

- Les six premiers contenus du catalogue plat sont simplement dupliqués dans une nouvelle rangée (`ui/BrowseFragment.kt:413-418`).
- Avec le catalogue actuel, cela privilégie mécaniquement TV Direct, Sport puis les premiers films ; il n’y a ni curation ni rotation.
- La rangée n’est créée que si elle est absente : l’arrivée du catalogue distant ne met pas à jour son contenu.
- Elle ne possède ni grand visuel, ni titre hero, ni résumé, ni actions propres. Il s’agit donc d’une rangée de doublons plutôt que d’une mise en avant.

**Recommandation :** choisir un hero éditorialisé ou dynamique, dédupliquer les contenus et le synchroniser avec le catalogue. Si aucun hero n’est prévu, renommer la rangée selon son contenu réel.

#### B. « Reprendre » ne respecte pas le filtre de progression — priorité haute

- `fraction()` exclut correctement les progressions ≤ 3 % et ≥ 95 % (`data/WatchProgressStore.kt:32-39`).
- `recent()` ignore ce filtre et retourne tout contenu ayant un timestamp, y compris direct, durée inconnue ou lecture à 0 % (`data/WatchProgressStore.kt:48-51`).
- `addResumeRow()` utilise `recent()` sans revérifier `fraction()` (`ui/BrowseFragment.kt:345-357`).
- La rangée n’est alimentée qu’au premier `bindCatalog()` (`ui/BrowseFragment.kt:421`) et il n’existe pas de rafraîchissement au retour du lecteur.

**Impact :** une chaîne en direct ou un contenu terminé peut apparaître sous « Reprendre », tandis qu’une nouvelle progression n’apparaît pas au retour.

**Recommandation :** ne sélectionner que les éléments avec progression utile, exclure les directs, régénérer la rangée au retour et proposer « Reprendre » / « Recommencer » dans la fiche.

#### C. Le catalogue visible et la grille peuvent diverger — priorité haute

`updateFilmRow()` remplace les cartes de Films par les tendances, mais ne met pas à jour `CatalogStore` (`ui/BrowseFragment.kt:444-460`). La grille « Tout voir » lit précisément ce store. Elle peut donc monter l’ancienne liste, différente de la rangée visible.

**Recommandation :** une seule source de vérité observable pour rangées et grille, avec mise à jour atomique par identifiant stable.

#### D. Les mises à jour distantes sont silencieuses et partielles — priorité moyenne

- Aucun indicateur ne signale que le catalogue affiché est local, en cours d’actualisation, distant ou obsolète.
- Les rangées absentes du nouveau catalogue ne sont pas retirées de l’adaptateur.
- Les rangées sont recherchées par titre plutôt que par `rowId`, ce qui fragile les renommages et doublons (`ui/BrowseFragment.kt:360-363,395`).
- La mise à jour par `clear()`/réinsertion doit être testée avec le focus dans la rangée : préserver l’adaptateur ne garantit pas à lui seul la conservation de l’élément focalisé.

**Recommandation :** identifiants stables, diff de rangées, statut discret de fraîcheur et test explicite du focus pendant l’actualisation.

#### E. Le fond ne reflète pas le contenu sélectionné — priorité basse, rendu à valider

Le fond est choisi uniquement par catégorie (`fondParDefaut(video.kind.name)`), pas à partir de l’illustration du contenu. Toutes les cartes d’une même catégorie produisent donc le même fond. Aucun `BackgroundManager.attach()` explicite n’apparaît dans le code applicatif : l’affichage effectif doit être vérifié sur appareil.

**Recommandation :** valider d’abord le rendu, puis utiliser le visuel du contenu avec fondu court et anti-rebond, en conservant le repli par catégorie.

#### F. Les catégories vides disparaissent sans explication — priorité basse

Les rangées vides et les placeholders sont retirés. C’est préférable à une tuile morte, mais l’utilisateur ne sait pas si Radio/Podcasts est indisponible, hors ligne ou inexistant.

**Recommandation :** pour une catégorie attendue mais vide, prévoir un état éditorial sobre ou un message dans la grille plutôt que faire disparaître silencieusement toute la destination.

### 4.2 Grille de catégorie

Outre le défaut majeur P1-01 :

1. **Aucun titre de catégorie affiché.** `title` est calculé mais jamais rendu (`ui/CategoryGridFragment.kt:44-55`). La ressource `grid_count` existe mais n’est pas utilisée.
2. **Aucun état vide ou erreur.** Si `CatalogStore` ne contient plus la rangée, l’écran peut être vide. Le store est uniquement en mémoire ; une restauration après mort du processus doit être testée.
3. **Aucun Retour visible.** Le bouton Retour système fonctionne en principe, mais l’écran manque de repères par rapport à la fiche détail.
4. **La carte « Tout voir » n’apporte pas de contenu supplémentaire.** La rangée horizontale contient déjà tous les éléments ; la grille change seulement la disposition. Pour Sport, Podcasts et Jeunesse, elle ne montre qu’un seul vrai contenu.
5. **Colonnes rigides.** 4/5/6 colonnes sont déterminées par le premier contenu, alors que `shapeHint` peut varier par carte. Sur petits écrans ou avec une police agrandie, les dimensions et marges fixes risquent de rogner la dernière colonne.
6. **Résolution des sources non unifiée.** Contrairement à l’accueil, la grille appelle directement `openDetails()` sans le chemin `sourceId → resolve`. Tout futur contenu non résolu présent dans le store risque d’échouer.

**Recommandation :** en-tête avec catégorie, nombre et Retour ; aucune carte récursive ; état vide ; source de données persistante ; navigation et résolution mutualisées avec l’accueil.

### 4.3 Recherche

1. **Portée non alignée avec l’icône globale — priorité moyenne.** L’unique moteur est `FilmSearchFragment`. L’utilisateur ne peut pas retrouver une radio, un podcast, une musique ou un contenu déjà visible. Le titre « Rechercher un film » n’apparaît qu’après l’ouverture.
2. **Aucun chargement visible — priorité haute.** `search_loading` existe mais n’est jamais utilisé. Pendant la résolution, seul un Toast temporaire est affiché.
3. **Écran vide ambigu — priorité haute.** Les résultats précédents sont effacés à chaque frappe avant le délai de 300 ms (`ui/FilmSearchFragment.kt:59-67`). Une recherche sans résultat ou en erreur ne produit un message que si elle a été explicitement soumise (`:74-95`).
4. **Pas d’action de récupération.** Aucun bouton Réessayer, aucun lien vers le catalogue hors ligne et aucune conservation des dernières recherches.
5. **Double activation possible.** Un résultat peut être sélectionné plusieurs fois pendant sa résolution ; rien ne verrouille l’action ni n’indique son état sur la carte.
6. **Fonctions de découverte absentes.** Pas de suggestions, historique, filtres, correction d’orthographe ou recherche par artiste/genre. Ce ne sont pas des bugs, mais des lacunes pour un catalogue qui s’agrandira.
7. **Entrée vocale à valider.** Tester autorisation refusée, absence de service vocal et retour au clavier sur Android TV et appareils tactiles.

**Recommandation immédiate :** conserver les résultats pendant le chargement, afficher les quatre états dans la page (initial, chargement, vide, erreur), et préciser la portée dès l’accueil. À moyen terme, recherche globale avec groupement par catégorie.

### 4.4 Fiche détail

#### A. « Ma liste » : état et action confus — priorité moyenne

- Quand le contenu est ajouté, le bouton affiche **« ✓ Dans ma liste »**, qui décrit un état mais pas l’action de retrait.
- L’icône reste `ic_add`, même après ajout.
- Seul `text` est mis à jour ; le `contentDescription` conserve l’ancienne valeur (`ui/DetailsFragment.kt:273-279`). TalkBack peut donc annoncer la mauvaise action.

**Recommandation :** libellé d’action explicite « Ajouter à Ma liste » / « Retirer de Ma liste », état adjacent si nécessaire, icône et description accessibles mises à jour ensemble.

#### B. Le signalement n’est pas envoyé — priorité haute sur la confiance

Le bouton est **« Envoyer »**, mais `ReportStore` ne fait qu’écrire dans `SharedPreferences` (`ui/DetailsFragment.kt:284-296`, `data/ReportStore.kt`). Aucun mécanisme de synchronisation n’est branché.

**Recommandation :** soit implémenter l’envoi avec état En attente/Envoyé/Échec, soit écrire clairement « Enregistrer le signalement sur cet appareil ». Le choix « Autre » devrait permettre une précision facultative.

#### C. Badge HD non fiable — priorité haute sur la confiance

Tout MP4 ou HLS non audio reçoit le badge HD (`ui/DetailsFragment.kt:153-164`). Le format de conteneur ne prouve pas la résolution. Le catalogue contient notamment une source Gaslight identifiée `576p-sd` qui serait néanmoins étiquetée HD.

**Recommandation :** ne montrer HD/Full HD que sur métadonnée ou piste vérifiée ; sinon ne pas afficher de badge qualité.

#### D. Catégorie Sport transformée en Film — priorité moyenne

`ContentKind.fromApi("sport")` retourne `MOVIE` pour les extraits non live. La fiche affiche donc la catégorie et le badge **Film** pour un contenu provenant de la rangée Sport (`data/Video.kt:35-43`, `ui/DetailsFragment.kt:96-98,153-154`).

**Recommandation :** conserver une catégorie d’affichage distincte du format technique de lecture.

#### E. Statut direct incohérent — priorité moyenne

- `video.isLive` tient compte de `forceLive`, mais le badge des cartes utilise `video.kind.isLive` (`ui/MissaCardPresenter.kt:104`).
- Le bouton principal reçoit seulement `video.kind`, donc un contenu `forceLive=true` peut être marqué direct ailleurs tout en proposant « Lire » au lieu de « Regarder en direct » (`ui/DetailsActions.kt:25-28`).
- Les 10 radios du catalogue sont marquées `live=false`, alors que leurs descriptions indiquent un flux audio en continu.

**Recommandation :** une seule propriété de présentation du direct pour carte, fiche et lecteur ; définir explicitement la sémantique des webradios.

#### F. Métadonnées pauvres et description tronquée — priorité moyenne

- `durationSec`, `creator`, `releaseYear` et `tags` ne sont pas exploités dans la fiche.
- Dans le catalogue livré, `duration_sec` est vide pour 39/39 contenus ; les deux épisodes n’ont aucun champ `series_title`, saison ou numéro structuré.
- La description est limitée à trois lignes sans action « Lire la suite » (`res/layout/fragment_details.xml:159-169`). Le commentaire « texte complet » du modèle ne correspond donc pas à l’accès réel au texte.
- Pas de langue, genre, classification d’âge ou informations de sous-titres.

**Recommandation :** prioriser année, durée, genre, saison/épisode et classification ; rendre la description extensible si elle est tronquée.

#### G. Parcours Séries incomplet — priorité moyenne

Il n’existe ni liste d’épisodes, ni sélecteur de saison, ni bouton Épisode suivant. Les deux éléments Séries actuels n’ont pas les champs structurés nécessaires : seul le titre contient « É1/É2 ».

**Recommandation :** regrouper par série et saison, afficher progression par épisode et enchaînement facultatif.

#### H. Contenus similaires incohérents — priorité moyenne

- Hors films, les suggestions viennent de `CatalogRepository.localCatalog()` et non du catalogue actuellement affiché (`ui/DetailsFragment.kt:327-341`).
- Radio actuelle : la rangée locale est vide, donc aucune chaîne similaire malgré les dix radios distantes.
- Musique actuelle : les suggestions peuvent être les anciennes démos plutôt que les morceaux affichés.
- Le libellé générique est « Chaînes similaires », y compris pour séries, musique ou jeunesse.
- La résolution d’un contenu similaire sans flux n’a pas d’indicateur de chargement.

**Recommandation :** utiliser la même source observable que l’accueil, adapter le titre à la catégorie et ajouter l’état de résolution.

#### I. Risques de rognage — à valider sur appareil

- La même illustration sert à un visuel 16:9 et au fond plein écran. Une affiche 2:3 sera fortement rognée verticalement dans le bloc 16:9.
- La rangée similaire est haute de 240 dp, alors qu’une affiche fait 195 dp, auxquels s’ajoutent titre, sous-titre, marges et zoom (`res/layout/fragment_details.xml:225-235`). Le bas des cartes Films peut être rogné.
- Le pavé horizontal d’actions n’a aucune variante pour petit écran ou police agrandie.

**Recommandation :** séparer pochette et backdrop, dimensionner la rangée au contenu maximal et tester 720p, 1080p, 4K et police maximale.

### 4.5 Lecteur vidéo et audio

1. **Erreur sans récupération : voir P1-05.**
2. **Musique non reconnue comme audio : voir P1-02.**
3. **Pas de lecture audio en arrière-plan — priorité haute pour une offre audio.** `onPause()` libère systématiquement le lecteur (`playback/PlayerActivity.kt:86-95`). Aucun `MediaSessionService` n’est déclaré. Quitter l’écran arrête donc radio, podcast ou musique ; pas de notification, reprise depuis l’accueil ou contrôle média système.
4. **Expérience Direct limitée — priorité moyenne.** Pas de zapping, « maintenant/à suivre », retour au direct après timeshift ou badge direct dans le lecteur. Le titre n’apparaît que quatre secondes au lancement (`playback/PlayerActivity.kt:64-78`).
5. **Reprise automatique sans choix — priorité moyenne.** La position est appliquée automatiquement entre 3 % et 95 %, sans « Reprendre à … » ni « Recommencer ».
6. **Pas de fin de parcours.** Aucun écran de fin, épisode suivant, contenu similaire ou retour guidé.
7. **Pas de réglages de lecture visibles.** Qualité/économie de données, vitesse et préférences audio ne sont pas exposés. Le contrôleur Media3 par défaut peut fournir certaines commandes de piste : leur visibilité et leur navigation D-pad doivent être validées plutôt que supposées absentes.
8. **Accessibilité média à traiter.** Le modèle de catalogue ne contient ni sous-titres, ni langue, ni audiodescription, ni transcription de podcast.
9. **Erreurs indifférenciées.** URL vide, réseau coupé, flux supprimé, format non pris en charge et échec DRM aboutissent au même message générique.
10. **Interruptions audio à valider.** Vérifier perte de focus audio, appareil Bluetooth déconnecté, appel/autre lecture et mode silencieux selon les appareils.

**Recommandation :** traiter le lecteur comme trois expériences distinctes — VOD, Direct et Audio — tout en partageant le même moteur Media3.

### 4.6 Jeux vidéo

1. **Progression absente : voir P1-08.**
2. **Pas d’état Installé ni action Ouvrir — priorité haute.** Après installation, la carte propose toujours le même clic de téléchargement. Le champ `installed` du downloader n’est pas observé et correspond seulement au lancement de l’installeur, pas à une installation confirmée.
3. **Action immédiate sans fiche — priorité moyenne.** Un clic lance le téléchargement. La description du jeu, l’éditeur, la source, les permissions et les conséquences ne sont pas présentés dans une étape de confirmation. La taille et la version sur la carte sont néanmoins un bon début.
4. **Double téléchargement possible.** Rien ne bloque un second clic sur le même jeu ; deux opérations peuvent viser le même fichier de cache.
5. **Pas d’annulation, reprise ou gestion de stockage.** Pas d’avertissement en cas d’espace faible ou de connexion limitée, et pas de nettoyage présenté à l’utilisateur.
6. **Repli hors ligne trompeur — priorité moyenne.** En cas d’échec, `GamesRepository` fournit une carte « Exemple · Jeu web » sans fichier. Son clic produit nécessairement une erreur (`data/GamesRepository.kt:24-36`).
7. **Erreurs trop techniques.** « Checksum SHA-256 », « Hôte non autorisé » et une URL brute ne sont pas des messages adaptés à l’utilisateur final. Conserver les détails techniques dans un volet de diagnostic.
8. **Écran initial vide pendant le chargement.** Aucun spinner/squelette ni message n’est affiché pendant l’appel réseau, dont le timeout peut atteindre 12 secondes.

**Recommandation :** une fiche jeu avec **Télécharger / Installer / Ouvrir / Mettre à jour / Désinstaller**, progression persistante, état réellement vérifié via le gestionnaire de paquets et véritable état hors ligne.

### 4.7 Extensions

1. **Accueil non rafraîchi : voir P1-07.**
2. **Actions destructives indétectables — priorité haute.** La suppression d’un dépôt ou d’une extension n’est disponible que par appui long (`extensions/ExtensionsAdapter.kt:92-100,128-143`). Aucun indice visuel ni bouton ne l’explique ; c’est également pauvre pour l’accessibilité.
3. **Conséquence de suppression sous-estimée — priorité haute.** Supprimer un dépôt désinstalle aussi toutes ses extensions (`extensions/ExtensionManager.kt:239-250`), mais la confirmation ne mentionne que le nom du dépôt (`extensions/ExtensionsActivity.kt:156-167`).
4. **« À jour » reste cliquable et réinstalle — priorité moyenne.** Le listener ne teste que compatibilité et disponibilité (`extensions/ExtensionsAdapter.kt:115-127`). Le libellé et l’action se contredisent.
5. **États désactivés incomplets.** Une extension désactivée peut afficher « À jour » sans action Réactiver/Réparer explicite. Une extension orpheline désactivée n’a aucun clic simple.
6. **État Occupé principalement textuel.** Ajouter et Actualiser sont désactivés, mais leurs couleurs ne changent pas ; les lignes restent activables et peuvent empiler des opérations sérialisées.
7. **Rafraîchissement de liste brutal.** `notifyDataSetChanged()` est appelé à chaque état (`extensions/ExtensionsAdapter.kt:33-51`), avec un risque de perte de focus/accessibilité pendant la navigation.
8. **Langage trop technique.** Chemins de fichiers internes, « API incompatible », URLs et exceptions sont affichés directement. Préférer un résumé utilisateur et des détails repliables.
9. **Confiance insuffisante pour du code tiers.** L’installation ne présente pas clairement éditeur, provenance, version, niveau de confiance ou risque d’exécuter une extension tierce.
10. **Entrée utilisateur imparfaite.** Valider avec une URL vide ferme le dialogue sans expliquer le problème (`extensions/ExtensionsActivity.kt:136-144`).
11. **Pluralisation et localisation.** Les libellés sont codés en dur et les résumés utilisent « dépôt(s) / extension(s) installée(s) » plutôt que des pluriels Android.

**Point positif :** l’écran possède déjà un statut global, des erreurs de dépôt visibles et des confirmations avant suppression ; il faut les rendre plus explicites plutôt que repartir de zéro.

---

## 5. Accessibilité

### 5.1 Contrastes mesurés

Calculs WCAG 2.x sur les couleurs opaques définies dans `res/values/colors.xml`.

| Couple | Ratio | Évaluation |
|---|---:|---|
| Jaune `#FFD400` / fond `#07111F` | **13,23:1** | Excellent |
| Texte foncé `#07111F` / jaune `#FFD400` | **13,23:1** | Excellent |
| Texte secondaire `#B8C5D6` / fond `#07111F` | **10,82:1** | Excellent |
| Texte secondaire / carte `#122338` | **9,07:1** | Excellent |
| Texte atténué `#718096` / fond `#07111F` | **4,72:1** | Passe de justesse pour du texte normal |
| Texte atténué / carte `#122338` | **3,95:1** | **Échec** pour le sous-titre 11 sp |
| Blanc / badge LIVE `#FF3B4F` | **3,51:1** | **Échec** pour le texte 12 sp, même gras |

**Corrections :** éclaircir `missa_text_muted` sur carte ; assombrir le rouge du badge ou utiliser un texte très foncé si le contraste approprié est atteint. Les contrastes sur photos et les doubles scrims doivent aussi être contrôlés sur de vraies vignettes.

### 5.2 TalkBack et sémantique

1. La carte « Tout voir » annonce **« Tout voir : Tout voir »**, car elle passe `video.title` au lieu de la catégorie contenue dans `description` (`ui/MissaCardPresenter.kt:114-120`).
2. Le `contentDescription` d’une carte normale ne contient que le titre : direct, saison/épisode et progression ne sont pas annoncés (`:121-123`).
3. Les en-têtes de rangée principale sont marqués `importantForAccessibility="no"` (`res/layout/lb_row_header.xml:28-39`) : le contexte de catégorie peut manquer pendant la lecture des cartes.
4. Le changement « Ma liste » ne met pas à jour la description accessible, comme indiqué plus haut.
5. L’image et le bouton lecture superposé ont chacun la description « Lire », créant potentiellement deux cibles identiques (`res/layout/fragment_details.xml:105-130`).
6. Le logo et son titre adjacent peuvent répéter « MISSA TV 360 » (`res/layout/custom_title_view.xml:15-36`). Le logo devrait être décoratif dans ce contexte.
7. Les appuis longs d’Extensions n’ont ni indication ni action d’accessibilité alternative.
8. Les Toasts sont utilisés pour des erreurs importantes ; ils sont temporaires et ne constituent pas un état durable ou systématiquement rejouable.

### 5.3 Cibles, typographie et mouvement

- Le bouton lecture fait **36 dp**, les actions **40 dp** et le Retour est un simple texte : sous les **48 dp** recommandés pour le tactile (`res/values/dimens.xml`, `res/layout/action_button.xml`).
- Les sous-titres de cartes à **11 sp**, badges à **12 sp** et source à **13 sp** sont petits pour une lecture à trois mètres ; à confirmer sur TV réelle.
- Les hauteurs fixes et les `maxLines` risquent de tronquer le texte lorsque la police système est agrandie.
- Les animations sont courtes (160 ms), ce qui est positif. Vérifier qu’elles respectent le réglage système de réduction/suppression des animations.

### 5.4 Accessibilité des médias

Prévoir dans le modèle et l’interface : sous-titres, langue audio, audiodescription lorsqu’elle existe, transcription des podcasts et réglage de taille des sous-titres. Aucun de ces éléments n’est actuellement représenté dans le catalogue.

---

## 6. Design visuel, contenu et cohérence de marque

### 6.1 Audit du catalogue livré

| Indicateur | Valeur | Lecture UX |
|---|---:|---|
| Contenus | **39** | Base réduite mais exploitable pour une démo |
| Catégories | **10** | Couverture large, profondeur très inégale |
| IDs dupliqués | **0** | Positif |
| Descriptions vides | **0** | Positif |
| Source et licence renseignées | **39/39** | Positif |
| Vignettes `placehold.co` | **23/39 — 59 %** | Majorité de visuels génériques |
| Durées renseignées | **0/39** | Impossible d’aider au choix par la durée |
| Épisodes avec série/saison/numéro structurés | **0/2** | Parcours Séries limité |
| Contenus `audio_only` | **17/39** | 10 radios + 1 podcast + 6 musiques |
| Musiques correctement reconnues comme audio par l’UI | **0/6** | Défaut de mapping P1-02 |

Profondeur par catégorie : Sport **1**, Séries **2**, Films courts **2**, Podcasts **1**, Jeunesse **1**. Les rangées très pauvres et les sections similaires masquées sous trois éléments donnent une impression de catalogue inachevé.

### 6.2 Répétition visuelle des titres

Les vignettes `placehold.co` contiennent déjà le titre. `card_missa.xml` ajoute le titre sur l’image puis une seconde fois sous l’image. L’utilisateur peut donc lire **trois fois le même titre** sur une seule carte. Les anciens visuels locaux contiennent aussi du texte.

**Recommandation :** privilégier des images sans texte ; afficher le titre une seule fois sous la carte, avec un overlay seulement s’il apporte une information différente.

### 6.3 Formats des visuels de repli

Les visuels locaux de cartes sont majoritairement en **320×180**, y compris ceux utilisés comme affiches 2:3 ou carrés. `centerCrop` évite la déformation mais rogne fortement : une source 16:9 dans une affiche 2:3 ne conserve qu’environ **37,5 % de sa largeur**.

Le repli Film `card_tbd.png` contient en outre **« À connecter / BFF »**, un jargon technique visible par l’utilisateur et susceptible d’être rogné.

**Recommandation :** créer de vrais replis 16:9, 2:3 et 1:1, sans jargon ni texte essentiel près des bords. Séparer les backdrops des pochettes.

### 6.4 Netteté des images

`ThumbnailLoader` sous-échantillonne vers une cible de **320 px** (`ui/ThumbnailLoader.kt:110,210-218`) puis le même bitmap peut servir de fond plein écran dans la fiche. Sur une TV 1080p/4K, cela produit un risque élevé de flou.

**Recommandation :** tailles de chargement adaptées à la cible (carte, pochette détail, backdrop), idéalement avec plusieurs résolutions fournies par le catalogue.

### 6.5 Marque et design system

- Le logo d’en-tête contient déjà « MISSA TV 360 », immédiatement suivi du même texte en 28 sp : répétition et petit texte illisible dans un logo de 52 dp.
- Le banner 320×180 est adapté au lanceur TV et très coloré ; l’interface est beaucoup plus sombre. Une version simplifiée du symbole serait plus cohérente dans l’en-tête.
- Plusieurs tokens sont dupliqués (`missa_text`/`text_primary`, `missa_navy`/`missa_bg_top`) et de nombreuses dimensions restent codées en dur malgré les tokens d’espacement.
- Deux présentateurs de cartes coexistent (`CardPresenter` et `MissaCardPresenter`), avec des règles différentes ; les jeux utilisent encore l’ancienne famille `ImageCardView`.

**Recommandation :** conserver la palette, mais unifier les tokens, les composants et les règles d’accessibilité avant d’étendre les écrans.

### 6.6 UX writing et localisation

- **« LIVE »** sur les cartes versus **« EN DIRECT »** sur la fiche : choisir une convention.
- **« Sport »** dans les données versus **« Sport Direct »** dans les ressources : la correspondance d’icône par libellé peut aussi échouer.
- **« Tout voir »** n’ajoute pas de contenu : « Afficher en grille » serait plus honnête si cette disposition est conservée.
- **« BFF », « Checksum SHA-256 », « API incompatible »** et chemins internes sont du jargon.
- Plusieurs textes de repli manquent d’accents (« Telechargement », « Jeux video », « Demonstration »).
- Les libellés de catégories sont codés en dur dans `ContentKind`, et de nombreux textes Jeux/Extensions sont codés dans Kotlin.
- Une seule famille `values` existe ; aucune localisation alternative ni ressource RTL spécifique. Plusieurs layouts utilisent `left/right` plutôt que `start/end`.

**Recommandation :** centraliser tous les libellés, employer les pluriels Android et faire une passe éditoriale française avant toute traduction.

---

## 7. Responsive TV et compatibilité tactile

Le manifeste déclare le tactile non obligatoire et ajoute une entrée `LAUNCHER` classique en plus de `LEANBACK_LAUNCHER`. L’application se présente donc aussi à des appareils tactiles, mais :

1. toutes les activités imposent le paysage ;
2. il n’existe que des ressources `layout` et `values` par défaut ;
3. les grilles utilisent des nombres de colonnes fixes ;
4. plusieurs cibles sont sous 48 dp ;
5. aucun parcours tactile spécifique n’existe pour les appuis longs ;
6. les blocs horizontaux de la fiche ne se recomposent pas sur petite largeur ;
7. les textes agrandis risquent d’être rognés par les hauteurs fixes.

**Décision produit recommandée :** soit positionner explicitement l’application comme Android TV et retirer/encadrer la promesse tactile, soit créer de vraies variantes responsive et tester les téléphones/tablettes. Une simple compatibilité technique du manifeste ne suffit pas à garantir une UX tactile.

### Matrice de validation du rendu

- TV 720p, 1080p et 4K, avec sur-balayage si le device l’applique ;
- Android API 21 (minimum), une version intermédiaire et API 35 ;
- téléphone et tablette en paysage si l’entrée tactile est conservée ;
- police par défaut et maximale ;
- D-pad seul, tactile seul, clavier et TalkBack ;
- textes longs, titres sur deux lignes, métadonnées absentes et catalogue volumineux.

---

## 8. États UI et performance perçue

### 8.1 Matrice des états

| Écran | Chargement | Vide | Erreur / hors ligne | Reprise / action |
|---|---|---|---|---|
| Accueil | Spinner très bref, puis démo immédiate ; enrichissement invisible | Rangées supprimées silencieusement | Échecs distants silencieux ; mauvaise source hors ligne | Aucun Réessayer explicite |
| Recherche | Aucun état dans la page | Blanc pendant frappe ; Toast seulement après soumission | Toast seulement après soumission | Pas de Réessayer ; résultats effacés à chaque frappe |
| Grille | Aucun | Grille potentiellement blanche si store absent | Aucun message | Retour système uniquement |
| Détail | Suggestions chargées silencieusement | Section similaire masquée sous 3 items | Toast pour certains contenus illisibles | Lecture, liste, signalement présents mais imparfaits |
| Lecteur | Contrôles/buffering Media3 par défaut à valider | URL vide = Toast + fermeture | Toast générique + fermeture | Aucun Réessayer intégré |
| Jeux | Écran vide pendant le réseau ; Toast au téléchargement | Fausse carte de démonstration en repli | Toast technique | Ni progression visible, ni annulation, ni Ouvrir |
| Extensions | Statut « Opération en cours » | En-têtes + messages partiels | Statut et Toast, parfois techniques | Actualiser présent ; autres actions à clarifier |

**Conclusion :** Extensions est l’écran le plus avancé en matière d’états. Les autres doivent adopter un composant commun **Chargement / Vide / Erreur / Hors ligne** avec une action principale.

### 8.2 Chargeur d’images

1. **Lecture non bornée.** `readBytes()` charge toute l’image en mémoire avant le sous-échantillonnage (`ui/ThumbnailLoader.kt:210`). Une image très grande ou un serveur incorrect peut provoquer mémoire excessive et à-coups.
2. **Types génériques rejetés malgré le commentaire.** `isDecodableType()` exige `image/*` ; `application/octet-stream` est donc refusé à la ligne 208 alors que le commentaire affirme l’accepter (`:117-118,202-208`).
3. **Cache mémoire incomplet.** Le chemin `load(ImageCardView)` écrit sur disque mais ne place pas le bitmap téléchargé dans le `LruCache`; le chemin `loadArtwork` le fait. Le comportement de cache varie donc selon le composant.
4. **Requêtes non annulées.** Les anciennes requêtes continuent après recycling ; le tag empêche une mauvaise affectation, mais pas la consommation réseau et les quatre threads peuvent rester occupés par d’anciennes cartes.
5. **Purge du cache seulement à l’initialisation.** Les écritures ne déclenchent pas de purge, donc la limite de 24 Mo n’est respectée qu’au prochain démarrage.
6. **JPEG systématique.** La conversion JPEG supprime la transparence et peut dégrader certains visuels ; choisir le format selon le contenu.
7. **Fallback positif.** En cas d’échec normal, le repli local évite une tuile vide : c’est un bon comportement à conserver.

### 8.3 Travail sur le thread UI

`installHeaderIcons()` parcourt récursivement tout l’arbre de vues à chaque événement de layout global (`ui/BrowseFragment.kt:271-300`). Sur une interface Leanback riche, cela peut contribuer aux à-coups de défilement. Brancher les icônes dans le presenter de header serait plus prévisible.

`ExtensionsAdapter.notifyDataSetChanged()` et les reconstructions de rangées doivent également être mesurés pendant le focus et le défilement.

---

## 9. Qualité et couverture de tests UX

### Constat

- **0 test Android instrumenté** (`androidTest`).
- Tests unitaires limités aux formes de cartes, libellés primaires et moteurs de sources.
- Aucun test de navigation D-pad, de focus, de TalkBack, de contraste, de rotation/configuration, de screenshot ou de parcours bout en bout.
- La CI Android assemble l’APK mais ne définit pas de tests UI ni de scénarios sur appareil.
- `PrimaryLabelTest` impose actuellement « Lire » pour `MUSIC`, alors que les six musiques livrées sont `audio_only=true` : le test verrouille une hypothèse contredite par les données.
- Les 54 XML inspectés sont bien formés ; cela ne valide ni leur rendu ni leur accessibilité.

### Tests de régression à ajouter en premier

1. Accueil → Tout voir → chaque type de carte : aucune fiche vide et aucun lecteur sans URL.
2. Accueil et grille affichent exactement les mêmes IDs après mise à jour des tendances.
3. Lancement en ligne, hors ligne avec cache, hors ligne sans cache et cache corrompu.
4. Ajout/retrait Ma liste → ouverture de la liste → survie au redémarrage.
5. Progression à 0 %, 3 %, 50 %, 95 %, 100 %, durée inconnue et direct.
6. Lecture audio pour Radio, Podcast et Music avec vérification du visuel et du libellé.
7. Erreur réseau du lecteur → Réessayer → succès ; Retour conserve la position.
8. Installation extension → retour Accueil → nouvelle rangée visible ; suppression → rangée retirée.
9. Jeu : chargement, progression, annulation, échec checksum, installation refusée, installation réussie puis Ouvrir.
10. Parcours complet D-pad avec vérification qu’un seul focus visible existe toujours.
11. TalkBack : catégorie, titre, état direct, progression, action Ma liste et suppression d’extension.
12. Captures 720p/1080p/4K avec police maximale et contenus longs.

---

## 10. Plan de correction recommandé

### Phase 1 — Fiabiliser les boucles essentielles

- Corriger la grille et interdire toute ouverture de lecteur sans source lisible.
- Propager `audioOnly` et unifier `isLive` dans tout le produit.
- Utiliser cache/asset hors ligne et identifier clairement le mode démo.
- Rendre Ma liste consultable ou retirer temporairement l’action.
- Ajouter une erreur de lecteur avec Réessayer/Retour.
- Garantir un focus visible sur chaque action de la fiche.
- Rafraîchir les rangées d’extensions au retour à l’accueil.
- Afficher la progression des jeux et bloquer les doublons de téléchargement.

**Critère de sortie :** les huit scénarios principaux aboutissent ou échouent avec une explication et une action de récupération ; aucun n’aboutit à une impasse silencieuse.

### Phase 2 — États, accessibilité et cohérence

- Composants communs chargement/vide/erreur/hors ligne.
- Recherche avec état de chargement et résultats conservés pendant la requête.
- Reprendre filtré et rafraîchi au retour.
- Actions Extensions visibles, avertissement de suppression en cascade et libellés non contradictoires.
- Corrections de contraste LIVE et texte atténué.
- Descriptions TalkBack complètes, cibles tactiles adaptées et libellés Ma liste corrigés.
- Fiche jeu complète avec état Installé/Ouvrir.
- HD basé sur une qualité réelle et signalement honnête sur sa destination.

**Critère de sortie :** parcours D-pad et TalkBack complets, contrastes ≥ 4,5:1 pour le texte normal, aucun message technique sans équivalent utilisateur.

### Phase 3 — Valeur de contenu et expérience média

- Hero réellement éditorialisé et dédupliqué.
- Recherche globale par catégories.
- Métadonnées durée/année/genre/série et description extensible.
- Vrais visuels aux trois ratios et backdrops haute définition.
- Lecture audio en arrière-plan avec MediaSession.
- Expérience Direct : identité de chaîne persistante, retour au direct et éventuellement zapping/EPG.
- Épisodes suivants et fin de lecture guidée.
- Réglages de qualité, économie de données, sous-titres et préférences.

**Critère de sortie :** les utilisateurs peuvent découvrir, choisir, reprendre et contrôler un contenu sans connaître l’architecture technique du produit.

---

## 11. Protocole de validation sur appareil

Pour transformer cet audit statique en validation UI/UX complète, exécuter les scénarios suivants sur au moins une TV réelle et un second appareil Android :

1. **Premier lancement en ligne** : mesurer le temps avant premier contenu et vérifier que le focus ne saute pas lors des actualisations.
2. **Lancement hors ligne** : confirmer la source affichée, sa date et l’accès à Réessayer.
3. **Navigation D-pad intégrale** : accueil, en-têtes, recherche, grille, détail, similaires, lecteur, jeux et extensions.
4. **Retour répété** : vérifier position, focus et absence de rechargement après plusieurs fiches similaires.
5. **Recherche** : résultats, aucun résultat, réseau lent, refus du micro et soumissions rapides.
6. **Lecture VOD** : pause, ±10 s, reprise, fin, erreur et nouvelle tentative.
7. **Direct** : coupure/reconnexion, timeshift éventuel et identification permanente de la chaîne.
8. **Audio** : Radio, Podcast et Music, sortie de l’activité, autres applications et interruptions.
9. **Jeux** : faible stockage, réseau lent, annulation, refus d’installation inconnue et lancement après installation.
10. **Extensions** : ajout invalide, dépôt indisponible, installation, mise à jour, suppression en cascade et retour Accueil.
11. **Accessibilité** : TalkBack, police maximale, contraste sur images claires et focus sur fond jaune.
12. **Rendu** : 720p/1080p/4K, affiches 2:3, cartes carrées, titres longs et rangées similaires.

Mesures utiles : temps jusqu’au premier contenu, nombre d’actions D-pad par tâche, taux de parcours sans impasse, temps de récupération après erreur, fluidité du défilement, mémoire des images et taux de vignettes réelles versus génériques.

---

## 12. Conclusion

L’application ne nécessite pas une refonte esthétique complète : **la palette, les formats de cartes et la base Leanback sont défendables**. La dette principale se situe dans la continuité des parcours et la vérité des états présentés à l’utilisateur.

Les corrections les plus rentables sont donc :

1. réparer les impasses de la grille et du lecteur ;
2. rendre Ma liste, Reprendre, Jeux et Extensions réellement utilisables de bout en bout ;
3. corriger le modèle audio/direct et la source hors ligne ;
4. uniformiser focus, contrastes, messages et états de chargement ;
5. remplacer progressivement les visuels génériques et enrichir les métadonnées.

Une fois la phase 1 appliquée, une session de validation sur TV permettra de trancher les seuls points encore dépendants du rendu : rognages, taille de lecture à distance, traversée du focus, contraste sur images et fluidité Leanback.

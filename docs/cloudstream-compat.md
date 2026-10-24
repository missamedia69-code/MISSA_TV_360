# Prototype Cloudstream — état et provenance

## Statut

Ce document décrit le prototype de compatibilité Cloudstream ajouté pour la
phase P3. Il ne constitue **pas** une déclaration de compatibilité générale avec
Cloudstream ni une validation de BuzzMonclick.

- **P0–P2 :** base d'installation, diagnostics et interface d'extensions en
  place.
- **P3 :** chargeur et pont implémentés, mais la porte de validation reste
  ouverte : aucune rangée réelle n'a encore été obtenue.
- **P4 :** extension progressive autorisée par l'utilisateur et limitée aux API
  identifiées par les binaires sondés. Elle est en cours, sans compatibilité
  globale ni lecture considérée comme acquise. Aucune rangée réelle n'a encore
  été obtenue sur les providers supplémentaires.

La sonde de référence est [`40037105337`](https://github.com/missamedia69-code/MISSA_TV_360/actions/runs/40037105337),
exécutée sur le commit `ce12d50` avec un émulateur Android API 34 `default`
x86_64. La CI standard de ce commit est verte ; cela ne remplace pas la porte
fonctionnelle P3.

## Surface effectivement implémentée

Le module `cloudstream-compat` fournit uniquement la surface nécessaire au
prototype inspecté :

- chargement du DEX par `PathClassLoader` ;
- lecture de la classe unique déclarée par `manifest.json`, sans essais de noms
  multiples ;
- instanciation de `BasePlugin`/`Plugin`, dispatch de `load()` et attachement des
  ressources lorsque `requiresResources` est vrai ;
- registre local des `MainAPI` et extracteurs, avec déchargement par fichier ;
- pont `MainAPI` → `Provider` MISSA ;
- signatures et règles R8 nécessaires aux types effectivement référencés par le
  prototype ;
- contexte applicatif minimal `CloudStreamApp.Companion.getContext()` requis par
  AllMovieLand, sans recopier l'application Cloudstream ;
- stdlib/compilateur Kotlin 2.1.0 pour la vraie classe `SpillingKt` référencée
  par ce binaire, avec les corrections de nullabilité Leanback associées ;
- `LiveSearchResponse`, `MainAPI.fixUrl`, `newLiveSearchResponse` et la surcharge
  multipage `newHomePageResponse(List, Boolean?)` requis par les DEX officiels
  Invidious/Twitch.

Le pont limite volontairement la lecture d'accueil à deux pages et à 24 cartes
par rangée. Une page Cloudstream est conservée comme `pageUrl` : elle n'est
jamais transformée en URL de flux. Une carte sans flux résolu n'est donc pas
annoncée comme lisible.

Les extracteurs complets, la résolution des liens, la lecture et la
compatibilité binaire avec d'autres plugins ne sont pas validés.

## Fixture BuzzMonclick et provenance

La sonde utilise le binaire réel suivant, téléchargé par la CI et non commité :

| Élément | Valeur |
|---|---|
| Dépôt | [`zzikozz/frenchCS`](https://github.com/zzikozz/frenchCS) |
| Référence épinglée | `90fbd7d9baa7579be4a6a804e02db6324b0cb1f1` |
| Fichier | `BuzzMonclick.cs3` |
| Taille | 41 771 octets |
| SHA-256 | `4024270cb486b718f58bbaa03b8cc9db1e14b94af0a758083e1bcb722dbb2293` |
| Classe du manifeste | `com.lagradost.BuzzMonclickProviderPlugin` |
| Version du manifeste embarqué | `1` |

Le workflow vérifie le SHA-256 avant l'exécution. Le motif
`app/src/androidTest/assets/*.cs3` est ignoré par Git afin d'éviter d'ajouter le
binaire au dépôt.

Le dépôt `builds` consulté pendant le diagnostic sert le même binaire que la
fixture épinglée, même si son index annonce des métadonnées de version
différentes. Cette divergence n'est pas corrigée par MISSA et ne doit pas être
utilisée pour inventer une version ou une provenance.

Aucune licence de ce binaire ou de ses contenus n'est déduite de sa présence
dans un dépôt public. Les contenus d'un plugin tiers doivent rester identifiés
comme tels ; le pont conserve le nom du provider dans `source` et laisse
`license` à `null` lorsqu'aucune licence n'est fournie. Aucune licence, affiche,
disponibilité ou métadonnée de contenu n'est fabriquée pour compléter le
catalogue.

## Résultat de la dernière sonde

Le vrai `.cs3` se charge et son `MainAPI` est enregistré. Le test atteint donc
l'appel des pages d'accueil. Les deux premières pages échouent ensuite avec la
même cause racine :

```text
java.net.UnknownHostException: Unable to resolve host "www.buzzmonclick.to": No address associated with hostname
```

Pages concernées : `PRÉFÉRÉ` et `TOUTES LES ÉMISSIONS`.

Conséquences :

- aucune rangée réelle n'est produite ;
- aucun symbole Cloudstream manquant n'a été identifié par cette exécution ;
- la porte P3 n'est pas franchie ;
- la navigation D-pad, la lecture et le redémarrage ne sont pas validés.

Les variantes de domaine examinées pendant le diagnostic n'ont fourni aucun
site de replay vérifiable. Aucun miroir SEO, domaine de parking ou faux contenu
n'est utilisé pour contourner ce résultat.

## Sonde supplémentaire FStream

À la demande de l'utilisateur, une seconde sonde a été ajoutée pour vérifier le
comportement du chargeur avec un autre binaire réel. Elle reste indépendante de
la porte BuzzMonclick et ne lance pas P4.

| Élément | Valeur |
|---|---|
| Dépôt | [`ayza/FStream` sur Disroot](https://git.disroot.org/ayza/FStream) |
| Commit du binaire | `ccf3042a8e814650ffad63b7aff3d834433937e0` |
| Fichier | `FStream.cs3` |
| Taille | 127 293 octets |
| SHA-256 | `9fb6ab0dd536be46550617704eef94c6cef2bf062dafdf0a061f436b3ea8a6bd` |
| Classe du manifeste | `com.lagradost.FStreamProviderPlugin` |
| Version du manifeste embarqué | `16` |

Le binaire est téléchargé et son empreinte est vérifiée par
`.github/workflows/p3-fstream-device.yml`. Il n'est pas commité. Aucune licence
n'est déduite de cette utilisation de test.

La sonde [`40047808962`](https://github.com/missamedia69-code/MISSA_TV_360/actions/runs/40047808962),
sur le commit `1bcf246`, s'arrête au chargement avec :

```text
java.lang.ClassNotFoundException: Didn't find class "com.lagradost.cloudstream3.syncproviders.InAppAuthAPIManager"
```

Cette absence est maintenant catégorisée `MISSING_DEPENDENCY`. Elle n'est pas
présentée comme une classe de manifeste invalide. Le classifieur P1 distingue
les formats `ClassNotFoundException` et `NoClassDefFoundError`, avec des tests
unitaires couvrant la classe du manifeste, la dépendance FStream et les
notations JVM/ART.

Aucune rangée FStream n'a été produite et aucun stub
`InAppAuthAPIManager` n'a été ajouté pour masquer cette incompatibilité. Cette
sonde supplémentaire ne valide ni BuzzMonclick, ni FStream, ni la lecture.

## Sondes P4 AllMovieLand, Invidious et Twitch

Ces trois providers sont diagnostiqués séparément. Leur résultat ne valide ni
BuzzMonclick, ni FStream, ni un autre `.cs3`. Les fixtures sont téléchargées par
la CI, vérifiées et jamais commitées.

| Provider | Référence épinglée | Taille | SHA-256 |
|---|---|---:|---|
| AllMovieLand v25 | `c0ae17606076e1fdda312bdf7be657617e3d7f7e` | 85 605 octets | `1282e16296bcb80dfe5c07e5aed8dda0ca4a744973abaa000ef0a3d714aa09d7` |
| Invidious v9 | `fcb6101ce646951f5055a6aff67ee2e9f3e1fd54` | 13 695 octets | `a0a2ff7e8484f69118598d347458774935733399ba0f76ef0d4921886a7db4a9` |
| Twitch v2 | `fcb6101ce646951f5055a6aff67ee2e9f3e1fd54` | 15 239 octets | `a7cc95f77bbd00a311d122201c63babe70b63fb398641fa28bf2b2c77346150a` |

AllMovieLand provient de
[`phisher98/cloudstream-extensions-phisher`](https://github.com/phisher98/cloudstream-extensions-phisher).
Invidious et Twitch proviennent de
[`recloudstream/extensions`](https://github.com/recloudstream/extensions).
Aucune licence de contenu n'est déduite de ces fixtures.

### AllMovieLand

Les sondes ont d'abord identifié `CloudStreamApp.Companion.getContext()`, puis
la vraie dépendance Kotlin `SpillingKt.nullOutSpilledVariable(Object)`. Après
ces raccords, le DEX atteint son réseau. La sonde
[`40098283093`](https://github.com/missamedia69-code/MISSA_TV_360/actions/runs/40098283093)
échoue sur :

```text
java.net.UnknownHostException: Unable to resolve host "mapi.elochkaigolochla.com"
```

La version v25 et son empreinte sont bien celles de la dernière entrée publiée
lors du diagnostic. Aucun miroir ou contenu de remplacement n'est injecté.

### Invidious

La sonde [`40100450808`](https://github.com/missamedia69-code/MISSA_TV_360/actions/runs/40100450808)
charge le DEX. Le provider déclare `hasMainPage=true` mais aucune `mainPage` ;
le pont ne fabrique donc pas de rangée à partir de cette seule déclaration. La
requête directe de diagnostic échoue sur `inv.nadeko.net` dans l'émulateur. Le
runner GitHub obtient quant à lui HTTP 502 sur l'API `popular` lors de ce run.
Il ne s'agit donc ni d'une rangée vide validée, ni d'une preuve de contenu
disponible.

### Twitch

La sonde [`40094396864`](https://github.com/missamedia69-code/MISSA_TV_360/actions/runs/40094396864)
a identifié les absences exactes `newLiveSearchResponse` et `fixUrl`. Les DEX
Invidious et Twitch référencent aussi la surcharge multipage
`newHomePageResponse(List, Boolean)`, ajoutée avec des tests de signature et de
comportement. La CI standard
[`40100450722`](https://github.com/missamedia69-code/MISSA_TV_360/actions/runs/40100450722)
est verte sur ce lot.

La validation fonctionnelle de bout en bout reste toutefois ouverte. Lors du
run `40100450808`, le runner obtient HTTP 403 sur `twitchtracker.com`, tandis
que l'émulateur échoue en DNS sur ses deux pages. Un run précédent avait obtenu
HTTP 200 côté runner sans rendre le DNS de l'émulateur fiable. Le DNS explicite
de l'émulateur n'a pas levé cette divergence. Aucun résultat Twitch n'est donc
présenté comme une rangée réelle ou une disponibilité de lecture.

## Reproduction et reprise

La CI standard exécute notamment :

```bash
python3 -m unittest discover -s tests -p 'test_*.py'
python3 -m unittest discover -s extension-tools -p 'test_*.py'
./gradlew :app:testDebugUnitTest :cloudstream-compat:testDebugUnitTest
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:assembleDebug
```

La sonde BuzzMonclick est définie dans
`.github/workflows/p3-cloudstream-device.yml`. Elle télécharge la fixture,
contrôle son empreinte, installe les APK et exécute le vrai test Android ; son
script Bash est `.github/scripts/p3_cloudstream_device.sh`. La sonde FStream
utilise `.github/workflows/p3-fstream-device.yml` et le même script avec un
filtre de classe distinct, afin qu'un résultat ne soit jamais attribué à l'autre
plugin.

Les sondes P4 sont définies dans
`.github/workflows/diagnostic-allmovieland.yml` et
`.github/workflows/diagnostic-official-providers.yml`. Elles contrôlent les
fixtures, relèvent les erreurs de chargement, de page, de dépendance et de
réseau séparément, puis exécutent le vrai DEX sur émulateur.

Pour franchir les portes fonctionnelles, il faut une version maintenue du
plugin ou une URL officielle vérifiable et accessible, puis obtenir une rangée
réelle avec la même sonde. P4 reste limité aux raccords éprouvés ; ce document
ne vaut ni validation du catalogue, ni validation de la lecture, du
redémarrage ou du D-pad.

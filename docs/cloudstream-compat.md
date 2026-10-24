# Prototype Cloudstream — état et provenance

## Statut

Ce document décrit le prototype de compatibilité Cloudstream ajouté pour la
phase P3. Il ne constitue **pas** une déclaration de compatibilité générale avec
Cloudstream ni une validation de BuzzMonclick.

- **P0–P2 :** base d'installation, diagnostics et interface d'extensions en
  place.
- **P3 :** chargeur et pont implémentés, mais la porte de validation reste
  ouverte : aucune rangée réelle n'a encore été obtenue.
- **P4 :** non lancé. L'extension à d'autres plugins et la lecture ne sont pas
  considérées comme acquises.

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
  prototype.

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

## Reproduction et reprise

La CI standard exécute notamment :

```bash
python3 -m unittest discover -s tests -p 'test_*.py'
python3 -m unittest discover -s extension-tools -p 'test_*.py'
./gradlew :app:testDebugUnitTest :cloudstream-compat:testDebugUnitTest
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:assembleDebug
```

La sonde émulateur est définie dans
`.github/workflows/p3-cloudstream-device.yml`. Elle télécharge la fixture,
contrôle son empreinte, installe les APK et exécute le vrai test Android ; son
script Bash est `.github/scripts/p3_cloudstream_device.sh`.

Pour reprendre P3, il faut une version maintenue du plugin ou une URL officielle
vérifiable et accessible, puis relancer cette même sonde. Tant que cette entrée
n'existe pas, P4 reste suspendu et ce document ne vaut pas validation du
prototype.

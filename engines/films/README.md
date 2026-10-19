# Moteur Films — paquet de sources

`manifest.json` est le paquet déclaratif lu par le moteur Films intégré à MISSA TV 360.
Il décrit les sources activées et les règles de classement/recherche/similarité. L'application
vérifie une nouvelle version à chaque ouverture, conserve la dernière version valide hors ligne,
puis utilise l'adaptateur natif correspondant.

## Source installée

- `internet_archive` : recherche de films indexés, classement des tendances par popularité,
  résolution des métadonnées et sélection d'un MP4; les recommandations utilisent les sujets
  et le créateur quand ces champs existent.
- Aucun filtre de droits n'est appliqué.

Le manifeste ne contient **pas de code exécutable**. L'application n'accepte que les adaptateurs
et opérations qu'elle connaît, avec validation des limites, champs et formats. Une nouvelle
source qui nécessite une nouvelle méthode d'accès doit ajouter un adaptateur natif à l'application;
les paramètres d'une source déjà supportée peuvent être mis à jour ici sans publier une nouvelle APK.

`engine.py` reste provisoirement le générateur de catalogue statique de compatibilité pour les
anciens consommateurs Pages pendant la migration. Le parcours dynamique de l'application utilise
le paquet versionné `manifest.json`.

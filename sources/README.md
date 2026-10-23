# Sources JSON

Ce répertoire est le seul point d’entrée des nouveaux catalogues de MISSA TV 360.
`index.json` est volontairement vide : aucune source n’est active.

Pour reconstruire les sources pas à pas :

1. créer un fichier `.json` dans ce répertoire ou un sous-répertoire ;
2. lui donner `schema_version: 1` et une liste `rows` ;
3. déclarer son chemin relatif dans `index.json` ;
4. ne renseigner que des métadonnées, licences et URL réellement connues ;
5. fournir des flux et vignettes HTTPS directs, sans résolution externe.

Tous les fichiers activés sont fusionnés dans un catalogue unique. Les rangées
portant le même identifiant sont regroupées. Les entrées invalides sont ignorées
et ne sont jamais remplacées par du contenu de démonstration.

Le contrat complet, les limites et les commandes de test sont décrits dans le
[README principal](../README.md).

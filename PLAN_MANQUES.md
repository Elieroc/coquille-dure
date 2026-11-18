# PLAN — Corriger les manques connus de Coquille dure

Suite de `PLAN.md` (étapes 1 à 8 codées le 2026-11-18). Ce plan traite les manques restants, par ordre d'exécution.
Règles inchangées : textes français exacts, build + installation + capture comparée à `docs/cookidoo_ref/` après chaque lot.
Build : `export ANDROID_SERIAL=emulator-5554 && bash /tmp/claude-1000/run.sh` (téléphone : `81a1f9f8`).

## État de départ

- `scraper/06_extract_details.py` tourne en arrière-plan : 2 204 / 10 005 recettes au 2026-11-18 (~0,5 recette/s, fin estimée dans ~4 h).
- Constat sur les 2 204 premiers détails : `devices` ne contient que `thermomix_sensor` (88 recettes). Il n'y a pas d'autres appareils ni d'accessoires dans cette source.
- Version actuelle : Room v9, `SEED_VERSION = 3`.

## Lot A — Données (débloque les lots B et C)

1. **Étendre `06_extract_details.py`** sans relancer de zéro :
   - ajouter dans `compact()` :
     - les titres de `recipeIngredientGroups` avec l'index des ingrédients de chaque groupe ;
     - `recipeUtensils` (id, nom, icône) ;
     - `usefulItems` ;
     - les descriptions de collection, si `inCollections` en fournit ;
   - ajouter un mode `--refetch-missing-fields`, qui repasse sur les fichiers déjà écrits sans ces champs.

   Ce mode est reprenable et garde un débit ≤ 2 req/s. Le lancer seulement après la fin du passage en cours.
2. **Variantes de portions** : enquête rapide (≤ 1 h).
   - Chercher dans les rels `.well-known/home` du service `recipes` un lien de cluster (`variantCluster.uid`).
   - Tester `GET {api}/recipes/cluster/{uid}` et les rels contenant `variant` ou `cluster`.
   - Si rien n'est trouvé : abandon documenté dans `REVERSE_NOTES.md`, et le stepper reste.
   - Si un endpoint existe : nouveau script `08_extract_variants.py` → `data/variants/{id}.json` (id de la variante, `servingSize`).
3. **`07_build_seed.py`** :
   - ingrédients : ajouter un champ `group` (titre du groupe) ;
   - colonnes `utensilsJson` (avec icônes) et `devicesJson` ;
   - `variantsJson` : liste de `{id, servings}` ;
   - table `collections` : ajouter une colonne `description`.
4. **App** :
   - migration Room v9 → v10 : `collections.description`, plus toute colonne nouvelle de `RecipeEntity` ;
   - `SEED_VERSION = 4` ;
   - régénérer le seed, puis build.

Critère : sur une recette FR connue (ex. r100577), la base contient tags, pays, groupes d'ingrédients et couverture de collection.

## Lot B — Détail recette

1. **Groupes d'ingrédients** : afficher un sous-titre gris avant chaque groupe non vide (comme les groupes d'étapes). `Ingredient` reçoit un champ `group`.
2. **Variantes de portions** (si A.2 réussit) :
   - segmented des portions serveur, sélection vert clair ;
   - un tap ouvre le détail de la recette variante, chargé dans la même vue ;
   - sinon, garder « + Adapter le nombre de portions ».
3. **Appareils et accessoires** :
   - section construite à partir de `tmVersions`, qui donne des tuiles TM7 / TM6 / TM5 / TM31 ;
   - plus `thermomix_sensor` dans `devicesJson` (« Capteur Thermomix® ») ;
   - icônes vectorielles locales dans `res/drawable/`, sans images Vorwerk ;
   - section masquée si les deux sources sont vides.
4. **Ustensiles utiles** : afficher l'icône CDN (`{transformation}` retiré) devant chaque ustensile. Tester l'URL brute comme pour les icônes d'ingrédients.
5. **« Trouver des plats similaires »** : un tap sur une chip de tag lance la recherche sur ce tag, via `SearchRequests.pending`, puis navigue vers Découvrir.
6. **« Également présenté dans »** : vignette de couverture + chevron ›, un tap ouvre `CollectionScreen`. Il faut passer `onCollectionClick` à `RecipeDetailScreen`.
7. **Étoiles partielles** : corriger le décalage de la 5e étoile dans `StarBar`. Utiliser un clip à largeur fixe (`Modifier.width(size * fill)` + `clipToBounds`) au lieu de `fillMaxWidth(fill)` dans une `Box` de taille fixe.

Critère : toutes les sections de [r0]…[r19] présentes quand les données existent.

## Lot C — Découvrir

1. **Carte liste compacte** `CompactRecipeRow` dans `ui/components/RecipeCard.kt` :
   - vignette 56 dp arrondie ;
   - titre gras sur 1 ligne avec ellipse ;
   - ligne « 4.6★(598) · 1h 20min ».
2. **Bannière éditoriale** [d2] :
   - carte grise arrondie : titre de collection, 4 `CompactRecipeRow`, bouton contour « Je découvre » qui ouvre `CollectionScreen` ;
   - source : une collection FR choisie chaque jour (seed = date) parmi celles qui ont une couverture et ≥ 6 recettes ;
   - placement : après « Recettes à la une ».
3. **Bannière inline dans les résultats** (basse priorité) :
   - si la requête correspond au nom d'une collection (ex. « poulet » → « Cuisiner le poulet ») ;
   - pleine largeur en 3e position : titre, description, bouton « Découvrez ».
4. **Suggestions de recherche** :
   - filtrer les mots vides (`pour, avec, sans, aux, des, les, une, sur, dans, et, ou, de, du, la, le`) ;
   - exiger au moins 3 lettres ;
   - classer par fréquence dans les titres plutôt que par ordre d'apparition.
5. **Filtre Accessoires** : il n'y a pas de données d'accessoires. Remplacer par une case « Capteur Thermomix® » (`devicesJson LIKE '%thermomix_sensor%'`) dans « Préférences de recherche », avec son compte. Ajouter `devices: Set<String>` à `RecipeFilter`.
6. **Catégorie « Menus et plus »** : vérifier le compte dans la feuille Catégorie. L'id `VrkNavigationCategory-rpf-000001303095` contient des minuscules : confirmer que `LIKE` matche (insensible à la casse en ASCII par défaut dans SQLite).

Critère : [d2] reproduit (bannière + chips), saisie « pou » sans « pour ».

## Lot D — Feuilles et ordre des actions

1. **Feuille ⋮ des cartes** : vérifier l'ordre exact de [h10] (Au menu aujourd'hui, Mes favoris, Ajouter à une liste, Ajouter à Ma semaine, Ajouter à la liste de courses, Annuler). Il y a un écart probable sur les icônes.
2. **Feuille ⋮ du détail** : vérifier l'ordre (Mes favoris, Ajouter à une liste, Ajouter à Ma semaine, Ajouter à la liste de courses, Ajouter à Mes créations, Partager, Annuler). Aujourd'hui, « Au menu aujourd'hui » y apparaît aussi : le retirer en mode `detail`, car le bouton vert fait déjà cette action.

## Lot E — Vérification sur appareil (rien n'est codé, uniquement des tests)

Parcours à rejouer sur l'émulateur, puis sur le téléphone, avec une capture par écran comparée à la référence. Toute anomalie est corrigée dans le lot concerné.

| Parcours | Référence | Vérifier |
|---|---|---|
| Feuilles Tri / Catégorie / Temps / Portions | [s12]…[s17] | compte des résultats mis à jour, chip verte si active |
| Écran Filtres | [s14]…[s16] | « Montrer N résultats » en direct, Réinitialiser |
| Onglet Collections + page collection | [s18][s19] | Sauvegarder → présent dans Mes recettes > Collections |
| Mes listes | [m*] | créer une liste depuis ⋮, ajout, suppression de la liste |
| Éditeur | [m8a]…[m14] | ✓ enregistre, la recette s'ouvre dans le détail standard, photo conservée |
| Mes créations | [m16] | dialogue de suppression, « Ajouter à Mes créations » depuis un détail |
| Ma semaine vue liste | [w1][w2] | sections, feuille ⋮, vue mémorisée après redémarrage |
| Profil | [p0]…[p5] | nom, photo, TM par défaut repris par la recherche |
| Persistance | — | note, notes perso et favoris conservés après `am force-stop` |

## Hors périmètre (inchangé)

FAB ✨ Assistant, Abonnement, Notifications, Traduire, Collections achetées : ces fonctions sont en ligne uniquement.

## Ordre et estimation

1. Lot A : attendre la fin de 06, puis environ 1 h de code et le refetch des champs manquants (~5 h machine si tout est refait).
2. Lot E, sur l'existant : ~1 h. Le faire en parallèle de la fin du scrape.
3. Lot B : ~2 h.
4. Lot C : ~2 h.
5. Lot D : ~30 min.
6. Lot E final, sur les deux appareils.

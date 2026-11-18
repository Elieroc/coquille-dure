# PLAN — Combler les écarts Coquille dure ↔ Cookidoo

Coquille dure est un clone Android **hors ligne** de l'app Cookidoo (Thermomix). L'objectif est une UI et une UX identiques à l'original.
Ce plan liste ce qui manque ou diffère, constaté le 2026-11-18 sur l'app officielle (téléphone) et dans le code Coquille dure.
Les captures de référence sont dans `docs/cookidoo_ref/*.png`. Les noms de fichier sont cités entre crochets, ex. [h2].

## 0. Contexte technique

- Stack : Kotlin, Jetpack Compose, Material3, Room (DB v5), Hilt, Coil, Gson. Package `fr.coquilledure`, sources dans `android/app/src/main/java/fr/coquilledure/`.
- Build et installation : `bash /tmp/claude-1000/run.sh`. Le script lance gradle `:app:assembleDebug`, puis `adb install -r` et relance l'app. Avec plusieurs appareils, ajouter `-s emulator-5554` ou `-s 81a1f9f8`. Env : `JAVA_HOME=~/Android/jdk`, `ANDROID_HOME=~/Android/sdk`.
- Données :
  - `android/app/src/main/res/raw/sample_recipes.json` (100 recettes) est seedé par `data/seed/DataSeeder.kt` (`SEED_VERSION`).
  - Scraper dans `scraper/` : `02_extract.py` produit `scraper/data/recipes/{id}.json` (83 081 recettes, toutes langues ; extraction **terminée** le 2026-11-18). `04_extract_ratings.py` est **terminé** : `scraper/data/ratings_bulk.json` (notes de toutes les recettes) et `scraper/data/ratings/{id}.json` (10 005 recettes FR avec note, compte et versions TM). Voir `scraper/REVERSE_NOTES.md`.
- Champs scrapés mais **non importés** dans Room : `collections`, `utensils`, `nutrition_groups` (forme complète), `url`, catégories avec id.
- Champs absents du scraper : tags, recettes liées, variantes de portions, pays, appareils et accessoires, prep time de la variante.
- Images : CDN `assets.tmecosys.com`. Coil les met en cache.
- Identifiants : uniquement via `.env` et des variables d'environnement. Ne jamais les écrire en dur.
- Fonctions **hors périmètre**, car en ligne : Cookidoo® Assistant (IA), Abonnement, Paramètres du compte, Notifications push, Traduire, Partager vers le web, Collections achetées.

## 1. Composants transverses (à faire en premier)

1. **Bottom nav** (`ui/navigation/CoquilleDureNavigation.kt`, `MainActivity.kt`)
   - Le libellé « Pour vous » devient « **Pour Vous** » (V majuscule).
   - Icônes : toujours en contour (outline), y compris sur l'onglet sélectionné, qui passe seulement en vert. Le libellé n'apparaît que sur l'onglet sélectionné (déjà en place).
   - Icônes proches de l'original : maison, loupe, livre ouvert, calendrier-liste, chariot [h2].
2. **Carte recette standard** (`RecipeImageCard`)
   - Titre en gras sur 2 lignes et ⋮.
   - Ligne « 4.8★(4.4K) » : compteur au format K (≥1000 → `4.4K`). Temps aligné à droite, au format `45min` / `1h 20min` [s4][d2].
   - Créer `formatCount()`.
3. **Carte overlay** (`OverlayRecipeCard`) : titre blanc et ⋮ sur l'image [h2]. À utiliser pour « Tout frais sorti du four » sur l'Accueil, en grille décalée 2 colonnes. L'Accueil utilise aujourd'hui des RecipeImageCard (écart).
4. **Carte liste horizontale compacte**
   - Vignette 56dp arrondie, titre gras sur 1 ligne avec ellipse.
   - Ligne « 4.6★(598) · 1h 20min ».
   - Utilisée dans les bannières éditoriales de Découvrir [d2].
5. **Feuille d'actions ⋮ d'une carte** (`RecipeActionMenu`) : ordre exact [h10] :
   1. Au menu aujourd'hui
   2. Mes favoris (icône pleine si favori)
   3. **Ajouter à une liste**
   4. Ajouter à Ma semaine
   5. Ajouter à la liste de courses
   6. Annuler

   « Ajouter à une liste » manque (voir §5).
6. **Feuille ⋮ du détail recette** : Traduire*, Mes favoris, Ajouter à une liste, Ajouter à Ma semaine, Ajouter à la liste de courses, **Ajouter à Mes créations** (duplique la recette en création modifiable), Partager*, Annuler.
   - *Traduire : masquer, car hors ligne.
   - *Partager : `Intent.ACTION_SEND` du titre et de l'URL cookidoo.
7. **Sélecteur de date « Ajouter à Ma semaine »** [w3]
   - Dialogue Material vert : en-tête vert avec l'année et la date « Mer. 18 nov. ».
   - Mois avec flèches ‹ ›, jours L M M J V S D, jour sélectionné en rond vert.
   - Seulement « Annuler » : **un tap sur un jour valide directement**, sans bouton OK.
   - Remplacer le `DatePickerDialog` + OK actuel.
8. **Snackbar** de confirmation : déjà en place (`AppMessages`).
9. **Typographie** (constaté sur l'émulateur) :
   - Coquille dure affiche la police système (Roboto). Cookidoo utilise une sans-serif géométrique arrondie, avec des titres légers et des cartes en gras [h2][d8].
   - Embarquer une police libre proche dans `res/font/` (ex. Figtree ou Outfit) et l'appliquer dans `ui/theme/Type.kt`.
   - Titres de section en poids Regular, titres de carte en Bold.
10. **Icônes de la bottom nav** : Coquille dure utilise aujourd'hui des icônes **pleines** quand l'onglet est sélectionné (maison pleine verte, livre plein). Elles doivent rester en contour, voir point 1.

## 2. Pour Vous (Accueil) — `ui/home/`

Référence : [h0]…[h9], [accueil].

- **Pastille flottante en haut** : « Commencez à explorer Cookidoo® » remplacé par « Commencez à explorer Coquille dure ». Bouton rond toque de chef à droite, qui ouvre le **Profil** (§8). Remplace la pastille « Coquille dure » actuelle.
- **Carrousel hero** (5 pages, points) : la carte blanche doit afficher « 4.8★(28) • 3 h 35 min » en plus du titre et du bookmark vert. Coquille dure n'affiche que le temps.
- « Tout frais sorti du four » : **grille décalée 2 colonnes de cartes overlay**, hauteurs alternées. Environ 8 recettes, puis la section suivante (pas une grille infinie).
- **Carrousels horizontaux thématiques**, à ajouter après la grille :
  - « Pour vos plats rapides du quotidien » : grandes cartes ~85 % de la largeur, image, titre, ⋮, note. Critère : totalTime ≤ 30 min.
  - « Cuisiner en tenant compte des glucides » : cartes plus petites, 3 visibles. Critère : glucides faibles via la nutrition.
  - « Des recettes végétariennes pour vous » : catégorie ou tag végétarien.
  - « Cuisine végétale » : tag vegan / végétalien.
- Sélection déterministe par jour (seed = date), comme aujourd'hui.
- **FAB ✨ vert** (Assistant) : hors ligne. Soit ne pas l'afficher, soit afficher une feuille « Fonction indisponible hors ligne ». Recommandé : ne pas l'afficher.
- Ignorer le dialogue « Quoi de neuf ? ».

## 3. Découvrir — `ui/search/`

Référence : [d0]…[d8], [s0]…[s19].

### 3.1 Page d'accueil Découvrir

1. Titre « Découvrir ».
2. Champ « **Parcourir Coquille dure** » avec bouton vert. Placeholder actuel : « Rechercher une recette ».
3. « Recettes à la une » : grille overlay décalée (déjà présente).
4. **Bannière éditoriale** sur carte grise arrondie :
   - titre, liste de 4 recettes compactes (§1.4) ;
   - bouton contour « Je découvre », qui ouvre une page collection (§3.4) [d2].
5. **« Des recettes en un clic »** : chips blanches empilées avec emoji (« Menu de la semaine 🍀 », « Vive les agrumes ! 🍊 »…). Lien « Afficher plus ⌄ » [d2]. Un tap lance une recherche ou une collection.
6. Carrousel « Découvrez tous les secrets de votre Thermomix® » (Tutoriels…) : **hors périmètre** ou contenu statique.
7. Titre « Coquille dure une inspiration sans limites ! », puis une **grille 2 colonnes de tuiles pastel** avec image et titre gras en bas [d7] :
   - Faites connaissance avec Cookidoo®, Apprendre avec Cookidoo®, Thermomix® conseils & astuces ;
   - Des ingrédients simples !, Cuisine de tous les jours, Régimes particuliers et tendances ;
   - Occasions spéciales et saisons, Autour du monde avec Cookidoo®.
8. **Page tuile** [d8] :
   - flèche retour ;
   - titre vert avec ⌄, qui ouvre un sélecteur des autres tuiles ;
   - bandeau image pastel avec le titre ;
   - paragraphe descriptif ;
   - « Des recettes en un clic » : chips ingrédient (Chocolat noir, Pomme, Fruits rouges, Pomme de terre, Carotte, Œuf, Poulet, Poisson, Tofu, Curcuma en poudre, Curry en poudre) ;
   - puis des carrousels de collections (Printemps / Été…).

   Chaque chip lance une recherche filtrée sur l'ingrédient. Contenu statique en JSON dans `res/raw/discover.json`.

### 3.2 Saisie et autocomplétion

- **Focus sans texte** : historique des recherches récentes (icône horloge, max 10, stocké en DataStore) [s1].
- **Pendant la saisie** [s2][s3] :
  - 5 suggestions de requêtes, avec la partie complétée **en gras** ;
  - un séparateur ;
  - des titres de recettes correspondants.

  Source : FTS sur les noms d'ingrédients et de recettes.
- Validation (IME Search ou bouton vert) : ouvre l'écran résultats et ajoute la requête à l'historique.

### 3.3 Résultats [s4][s12]…[s18]

- Barre : flèche retour, champ avec ✕ d'effacement, bouton vert.
- **Onglets** « Recettes N » / « Collections N » avec pastille de compte (verte si sélectionnée). L'onglet Collections manque.
- **Rangée de chips** : icône tri, icône filtres, Catégorie, Temps, **Portions** (Portions manque). Une chip active est verte. Chaque chip ouvre une **bottom sheet**, pas un DropdownMenu.
- **Tri** [s12] : Pertinence ✓, Plus récent, Nom, Temps total le plus court, Temps de prép. le plus court, Mieux notés. « Plus récent » = ordre décroissant des ids ou date de scrape.
- **Catégorie** (sheet) : case + icône ligne + nom + compte. Liste fixe :
  - Entrées, Soupes, Pâtes et riz ;
  - Plats principaux : viande / poisson / végétarien / autre ;
  - Accompagnements, Desserts et friandises, Tartes, quiches et pizza ;
  - Pâtisserie, Pains et viennoiseries, Boissons, Basiques, Nourriture pour bébé ;
  - Sauces, dips et tartinades - Salées ;
  - Petit déjeuner, En-cas, Menus et plus.

  Multi-sélection. Mapper via les ids `VrkNavCategory-RPF-0xx`.
- **Temps** (sheet) : deux segmented, « Temps de préparation » ≤15 / ≤30 / ≤45 min et « Temps total » ≤15 / ≤30 / ≤45 min. Remplace 15 / 30 / 60 / 120.
- **Portions** (sheet) : 1 / 2 / 4 / 6 / 8+.
- **Filtres** (plein écran, onglets) :
  - « Filtres de cuisson » :
    - Ingrédients inclus (recherche avec puces) ;
    - Ingrédients exclus (suggestions « oignon 490 résultats ») ;
    - Tags ;
    - Évaluations (cases 5★ / 4★ / 3★ / 2★ et plus, avec comptes) ;
    - Niveau : segmented facile / moyen / difficile.

    Pied : « Réinitialiser » + bouton vert « Montrer N résultats », avec un compte live.
  - « Préférences de recherche » :
    - Mes appareils, segmented TM7 / TM6 / TM5 / TM31, filtre sur `tmVersions` ;
    - Accessoires Thermomix®, cases avec comptes, si les données le permettent ;
    - « Mettre à jour le filtre par défaut », persisté en DataStore et partagé avec Profil > Mes préférences.

    Pays et Langue : ignorer (FR seulement).
- **Bannière éditoriale inline** dans la grille de résultats, ex. « Cuisiner le poulet » + description + bouton « Découvrez ». Optionnel, basse priorité.
- **La recherche est limitée à `LIMIT 50`** dans `RecipeDao.searchRecipes` : supprimer la limite ou paginer (Paging 3), car Cookidoo affiche « 645 ». Les filtres doivent s'appliquer en SQL, pas en mémoire.

### 3.4 Collections [s18][s19]

- Onglet Collections : chips tri / filtre / Catégorie, cartes 2 colonnes (couverture + titre).
- **Page collection** :
  - carte d'en-tête bleu clair : couverture, titre, « 6 Recettes », description ;
  - bouton contour « **Sauvegarder** », qui l'ajoute à Mes recettes > Mes recettes N Collection ;
  - grille des recettes.
- Données : le champ `collections` (id, name, total_recipes) existe dans le scrape. L'index inverse collection → recettes est à construire au build du seed. Filtrer les collections FR (id contenant `fr`).

## 4. Détail recette — `ui/recipe/RecipeDetailScreen.kt`

Référence : [detail_top], [detail1], [r0]…[r19].

| Élément | Cookidoo | Coquille dure |
|---|---|---|
| Barre sur l'image | retour, Traduire (masquer), bookmark | OK |
| Barre repliée | titre + bookmark vert | OK |
| Note | **5 étoiles partielles** + « 3.3 (20) », tap = sheet Évaluer | ligne compacte |
| Bouton principal | « 📅 **Au menu aujourd'hui** » vert + ⋮ gris | « Commencer la cuisson » |
| Portions | segmented des **variantes serveur** (ex. 2 / 3, sélection vert clair) **ou** « + Adapter le nombre de portions » | stepper linéaire seulement |
| Ingrédients | **icône 48dp** + quantité + nom, note grise, **titres de groupes** | texte seul, sans groupes |
| Préparation | numéros, sous-titres de groupe, réglages TM **en gras**, modes (« Rice cooker 🍚 ») en gras avec icône | gras partiel |
| Conseil(s) | titre « Conseil(s) » | « Conseils » (renommer) |
| Mes notes | absent dans Coquille dure | à faire |
| Appareils et accessoires | images TM7 / TM6, four… | absent |
| Ustensiles utiles | puces | absent (données `utensils` dispo) |
| Niveau | toque + facile / moyen / difficile | absent (`difficulty` dispo) |
| Infos nut. « par 1 portion » | Calories kJ/kcal sur une ligne, Protides, Glucides, Lipides, Graisses saturées, Fibre, Sodium | libellés différents (Protéines, Fibres, Énergie séparée) |
| Pays | globe + France | absent |
| Trouver des plats similaires | chips tags | absent |
| Également présenté dans | lignes collection (vignette, titre, « 6 Recettes, France », ›) | absent |
| Vous pourriez aussi aimer… | carrousel cartes avec note | absent |

À faire :

1. Remplacer « Commencer la cuisson » par « Au menu aujourd'hui », qui ajoute la recette à Ma semaine à la date du jour avec un snackbar.
   - Le mode pas-à-pas n'existe pas sur l'app mobile Cookidoo, qui envoie la recette au Thermomix.
   - **Garder** le mode cuisson Coquille dure, accessible depuis la feuille ⋮ (« Mode pas-à-pas ») ou par un bouton secondaire en bas de la section Préparation. Décision : bouton secondaire contour « Cuisiner pas à pas ».
2. Barre d'actions sticky en bas au scroll : déjà en place, à adapter au nouveau bouton.
3. **Sheet « Évaluer cette recette »** [r*] : 5 boutons étoile ronds, « Annuler » / « Sauvegarder » désactivé tant qu'aucune note n'est choisie. Stocker `userRating` en local et l'afficher en remplaçant la moyenne.
4. **Mes notes** :
   - bloc fond gris : « Mes notes », « Cette note n'est visible que par vous… », « ✎ Ajouter des notes » ;
   - sheet : textarea « Ajouter des notes », compteur « 0 / 1000 », bouton « Oui, je confirme » ;
   - affichage de la note ensuite, avec modification possible ;
   - table `recipe_notes`.
5. Portions :
   - variantes serveur si présentes (données à scraper : endpoint `recipes/recipe/fr-FR/{id}` → `servingSize` des variantes / `recipeVariants`) ;
   - sinon « + Adapter le nombre de portions » puis un stepper (existant).
6. Icônes d'ingrédients : URL CDN si disponible dans les données détaillées (`ingredientNotation`/`image`). Sinon, placeholder rond neutre.
7. Sections Ustensiles, Niveau, Pays, Similaires, Collections, « Vous pourriez aussi aimer… » :
   - recettes de la même catégorie principale, triées par note, en excluant la recette courante ;
   - masquer une section si ses données sont vides.

## 5. Mes recettes — `ui/favorites/`

Référence : [lib], [m0]…[m17].

- En-tête : titre « Mes recettes » + **icône tri** (sheet : A - Z / Date de création / Sauvegarder).
- Recherche « Rechercher dans Mes recettes » (en place).
- Carte groupée « Mes favoris N Recette » / « Mes créations N Recette », puis carte « Mes recettes N Collection » : en place, mais Créations et Collections sont **inertes**.
- **FAB vert +** → sheet :
  - Créer une nouvelle liste de recettes ;
  - Créer une recette ;
  - Importer une recette (hors périmètre : masquer ou désactiver) ;
  - Annuler.
- **États vides illustrés** :
  - Favoris : « Pas de favoris pour l'instant… ». Le texte Coquille dure actuel diffère.
  - Créations : « Ajouter vos propres recettes / Importer, créer ou modifier des recettes n'a jamais été aussi facile ! Vous y êtes presque » + FAB.
  - Collections : « Vous n'avez pas encore enregistré de collection… » + bouton « Trouver des collections » → Découvrir, onglet Collections.
- **Listes personnalisées** (« Ajouter à une liste ») :
  - plein écran avec X et état vide ;
  - bouton vert « Créer une liste » → « Créer une nouvelle liste de recettes » ;
  - champ « Nommer votre liste » 0/40, ✓.

  Tables `recipe_lists` et `recipe_list_items`. Les listes apparaissent dans la carte « Mes recettes N Collection » avec les collections sauvegardées.
- **Mes créations** :
  - liste « N/300 Recettes », recherche, tri ;
  - carte avec placeholder gris + badge toque.
  - ⋮ : Au menu aujourd'hui, Ajouter à Ma semaine, Ajouter à la liste de courses, Modifier la recette, **Supprimer la recette** (dialogue « Supprimer la recette / Voulez-vous vraiment supprimer cette recette ? / Annuler · Supprimer ») [m16], Annuler.
- **Création** :
  - écran illustré « Créez votre propre recette », champ « Donnez un nom à votre recette » 0/120, bouton « Créer » ;
  - puis l'éditeur « Modifier la recette » (✓ en haut).
- **Éditeur** :
  - photo : motif + « Ajouter une photo », photo de la galerie via Photo Picker ;
  - titre ;
  - lignes « Ajouter le temps de préparation », « Ajouter le temps total », « 4 portions », chacune avec un crayon vert ;
  - Ingrédients : état vide + « Ajouter les ingrédients » ;
  - Étapes de préparation + « Ajouter des étapes » ;
  - Conseil(s) ;
  - Appareils et accessoires (TM6 + crayon).
- **Éditeur d'étapes** :
  - onglets Ingrédients / Étapes ;
  - « Étape N » avec X / ✓, champ « Décrire l'étape » ;
  - boutons lame et balance ;
  - « Ajouter un paramètre » : sheet à onglets Manuel / Modes, min / sec, Température (DÉSACTIVÉE / 37–120 °C), Vitesse ;
  - liste des étapes avec « … » et FAB + ;
  - bouton « Oui, je confirme ».
- Stockage : table `user_recipes` au même schéma JSON que `recipes`, avec un id préfixé `u`. Une recette créée doit s'ouvrir dans le détail standard.

## 6. Ma semaine — `ui/planner/PlannerScreen.kt`

Référence : [week1], [w0]…[w3].

- **Vue colonnes** (par défaut) :
  - en-tête « novembre 2026 ⌄ » (tap = sélecteur de date §1.7) ;
  - icône **liste** à droite pour changer de vue ;
  - 3 colonnes de jours visibles, défilement horizontal ;
  - aujourd'hui : « AUJOURD'HUI » vert + numéro vert, fond gris ; autres jours : « JE » / « VE » + numéro ;
  - cartes recette dans la colonne : image, titre gras, ⋮.
- **Vue liste** [w1] :
  - titre « Ma semaine » + icône calendrier pour revenir ;
  - sections par jour (« Au menu aujourd'hui », puis les jours suivants) ;
  - lignes : vignette, titre gras, ⋮.
- **⋮ d'une entrée** = **sheet** (pas un DropdownMenu) [w2] :
  - Changer de jour ;
  - Ajouter à la liste de courses ;
  - Supprimer ;
  - Annuler.

  Les libellés actuels « Changer de date » et « Retirer du menu » sont à renommer.
- Mémoriser la vue choisie (DataStore).

## 7. Courses

Déjà répliqué ([shop1]). Vérifier seulement que « Ajouter à la liste de courses » depuis Ma semaine et Mes créations l'alimente.

## 8. Profil (nouveau) — ouvert par la toque de l'Accueil

Référence : [p0]…[p5].

- En-tête : « Bienvenue / Appuyez pour ajouter un nom et une photo », stockés en local (nom + photo).
- Entrées à garder :
  - **Mes appareils** (TM7 / TM6 / TM5 / TM31, pilote le filtre par défaut) ;
  - **Mes préférences** :
    - chips de profil culinaire (Régime végétarien, Facile pour le quotidien, Créatif et curieux, Cuisine traditionnelle), qui pondèrent l'Accueil ;
    - préférences de filtre de recherche : Accessoires en cartes « Propriété de ✕ » ;
    - bouton « Mettre à jour le filtre ».
  - **Mentions légales** : texte statique « projet personnel, données Cookidoo® ».
- Entrées à masquer : Notifications, Abonnement, Paramètres du compte, Collections achetées, Tutoriels, Utilisation de mes données, Se déconnecter.

## 9. Données — extensions du pipeline

1. **Import Room enrichi** : ajouter à `RecipeEntity` (migration v5 → v6) :
   - `utensilsJson`, `collectionsJson`, `categoryIds` ;
   - `tagsJson`, `country`, `devicesJson`, `variantsJson`, `relatedIds` ;
   - `nutritionPerServingJson`.
2. **Scraper** : nouveau `scraper/06_extract_details.py`, qui appelle `GET {api}/recipes/recipe/fr-FR/{id}` (déjà utilisé par 04).
   - Champs à récupérer : `tags`, `recipeVariants` / portions, `thermomixVersions`, appareils et accessoires (`requiredDevices` / `accessories`), images d'ingrédients, `locale` / pays.
   - Débit ≤2 req/s, reprenable, uniquement les recettes FR (marqueur `vitesse` ou locale fr-FR).
   - Ne pas relancer `02_extract.py` / `04_extract_ratings.py` s'ils tournent encore.
3. **Générateur de seed** : nouveau `scraper/07_build_seed.py`.
   - Filtre les recettes FR.
   - Construit :
     - les recettes ;
     - l'index des collections (id, nom, description, couverture, recipeIds) ;
     - l'index des tags ;
     - les catégories avec compte ;
     - `discover.json`.
   - Sortie : une **base SQLite pré-remplie** `assets/coquilledure.db` chargée via `Room.createFromAsset`, plutôt que le JSON actuel (taille).
   - Images : Coil + CDN en ligne au premier affichage. Mode 100 % hors ligne via `03_download_images.py`, optionnel.
4. FTS : indexer aussi les noms d'ingrédients (pour l'autocomplétion et les filtres d'ingrédients).

## 10. Ordre d'implémentation et critères d'acceptation

1. **Données** (§9) : migration Room, seed SQLite, DAO paginés et filtrés en SQL.
   - Critère : recherche « poulet » → compte réel (>50), filtres SQL < 300 ms.
2. **Composants transverses** (§1).
   - Critère : comparer avec [h2], [s4], [h10], [w3] à la même échelle.
3. **Détail recette** (§4).
   - Critère : toutes les sections de [r0]…[r19] présentes quand les données existent ; notes et évaluation persistées après redémarrage.
4. **Découvrir et Recherche** (§3).
   - Critère : historique, autocomplétion en gras, 5 sheets de filtres, onglet Collections, page collection avec Sauvegarder.
5. **Mes recettes** (§5) : listes, collections sauvegardées, créations (création, édition, suppression).
6. **Ma semaine** (§6) : vue colonnes, vue liste et sheets.
7. **Accueil** (§2) : carrousels thématiques, hero noté, pastille avec Profil.
8. **Profil** (§8).

Règles générales :

- Textes français **exacts** de ce document : respecter majuscules, « Conseil(s) », espaces insécables avant « ? » et « ! ».
- Vert primaire et formes déjà définis dans `ui/theme/`.
- Après chaque étape : build, installation sur l'émulateur, capture d'écran comparée à la référence `docs/cookidoo_ref/`.

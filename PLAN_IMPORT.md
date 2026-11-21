# PLAN — Importer ses données Cookidoo depuis l'application

Objectif : depuis Coquille dure, l'utilisateur se connecte à son compte Cookidoo, choisit ce qu'il veut récupérer, puis appuie sur « Importer ». L'app télécharge en une fois ses créations, ses listes, ses favoris, Ma semaine et sa liste de courses, et les recopie en local. Ensuite, l'app fonctionne de nouveau sans réseau.

Établi le 2026-11-18 à partir de `cookidoo-api` 0.19.0 (`.venv`), qui sert de référence pour les endpoints et le login. Le schéma Room est en v10.

## État d'avancement (2026-11-18)

Fait et testé sur `qa_pixel_api36` avec le compte de test (2 favoris, 1 liste, 3 en Ma semaine, 3 en courses) :
connexion WebView, client API Kotlin (`data/cookidoo/`), collecte, import transactionnel (`CookidooImporter`),
écrans (`ui/importer/`), fusion et remplacement, réimport sans doublon, service `created-recipes` en panne toléré.
Non vérifié : créations (service en panne, format supposé d'après `cookidoo_api`), collections enregistrées (compte à 0),
évaluations (format d'une réponse 200 inconnu), nom du profil (vide sur ce compte), Android 8, téléphone réel,
connexion Google/Apple, tests unitaires, `.zip`/images des créations (code écrit, jamais exécuté).

## 0. Principes

- **L'import est ponctuel, sans synchronisation.** On ne garde aucun token après l'import et l'app n'écrit jamais sur le compte Cookidoo (aucun POST, PATCH ou DELETE côté API, sauf l'échange de token).
- **Le mot de passe ne passe jamais par notre code.** L'utilisateur le saisit sur la page de connexion officielle, affichée dans une `WebView`. L'app récupère seulement le code OAuth.
- **Le réseau est réservé à cette fonction.** La permission `INTERNET` est déjà dans le manifeste, pour Coil.
- **Tout le code est en Kotlin.** `cookidoo-api` sert de spécification (chemins, formats JSON) : il n'est pas embarqué dans l'app.

## 1. Parcours utilisateur

1. Profil → nouvelle ligne **« Importer depuis Cookidoo »**.
2. Écran d'intro : « Connectez-vous à votre compte Cookidoo pour récupérer vos recettes, listes, favoris, Ma semaine et liste de courses. Votre mot de passe est saisi sur la page officielle Cookidoo et n'est pas conservé. » Bouton **Se connecter**.
3. La WebView affiche la page de connexion Cookidoo officielle. L'utilisateur s'y connecte, avec Google ou Apple si la page le propose.
4. Un écran **Analyse** affiche un chargement, puis un résumé par catégorie avec une case cochée par défaut :
   - ☑ Mes créations (12)
   - ☑ Mes listes (4 · 87 recettes)
   - ☑ Favoris (38)
   - ☑ Collections enregistrées (3)
   - ☑ Ma semaine (9 recettes à venir)
   - ☑ Liste de courses (5 recettes · 7 articles)
   - ☐ Mes évaluations (plus lent)
   - ☑ Nom et appareils
5. Choix de la fusion : **Ajouter à mes données** (par défaut) ou **Remplacer**. Remplacer ouvre une confirmation : « Vos listes, favoris… actuels seront effacés. »
6. Bouton **Importer**. Une barre de progression avance par catégorie, avec un bouton Annuler.
7. Écran de fin : « 12 créations, 4 listes, 38 favoris importés. 3 recettes introuvables dans Coquille dure. » Un bouton « Voir » affiche les introuvables, avec nom et image si disponibles.
8. Les erreurs sont lisibles et proposent de réessayer : pas de réseau, connexion refusée, session expirée, Cookidoo a changé.

## 2. Données importées

| Donnée | Endpoint (via `.well-known/home`) | Réf. `cookidoo-api` | Cible Room |
|---|---|---|---|
| Mes créations | `created-recipes/{lang}` puis `/{id}` | `list_custom_recipes`, `get_custom_recipe` | `recipes`, id `ck_<id>` |
| Mes listes | `organize/{lang}/api/custom-list` (paginé) | `get_custom_collections(page)` | `recipe_lists` + `recipe_list_items` |
| Collections enregistrées | `organize/{lang}/api/managed-list` (paginé) | `get_managed_collections(page)` | `saved_collections` |
| Ma semaine | `planning/{lang}/api/my-week/{day}` | `get_recipes_in_calendar_week` | `planned_recipes` |
| Courses : recettes | `shopping/{lang}` | `get_shopping_list_recipes` | `shopping_recipes` |
| Courses : ingrédients | `shopping/{lang}` | `get_ingredient_items` | `shopping_items` |
| Courses : articles libres | `shopping/{lang}` | `get_additional_items` | `shopping_items` (`recipeId = null`) |
| Nom | `community/profile/{lang}` | `get_user_info` | prefs du Profil |
| Appareils TM | `customer-devices/api/my-devices/versions` | `get_devices` | préférence « Mes appareils » |
| Favoris | `organize/{lang}/api/bookmark?page=N` (rel `organize:api-bookmark`, paginé) | `GET` brut, absent de la lib | `recipes.isFavorite` |
| Évaluations | `rating/{lang}/user-ratings/recipes/{id}` (une requête par recette, 404 si aucune ; pas de dump groupé) | absent | `recipe_ratings` |
| Notes perso | `GET recipe-notes/{lang}/recipes/{id}` (une requête par recette, 204 si aucune) | brut, absent de la lib | `recipe_notes` (à faire) |

Hors périmètre : abonnement, historique de cuisson, collections achetées non enregistrées.

## 3. Étapes de réalisation

### Étape 1 — Repérage de l'API (FAIT le 2026-11-18)

Résultats détaillés dans `scraper/REVERSE_NOTES.md` §4, script `scraper/10_discover_user.py`. Favoris trouvés (`organize/{lang}/api/bookmark`), notes perso inexistantes, évaluations sans dump groupé. Formats confirmés sur un compte rempli (favoris, listes, Ma semaine, courses). Restent à confirmer : collections enregistrées et créations (`created-recipes` est en panne côté Cookidoo (502 sur tout le service, page de maintenance, constaté le 2026-11-18), à retester ; l'import doit tolérer cette erreur, continuer et la signaler dans le rapport). Plan initial de l'étape :

1. Script `scraper/10_discover_user.py`, lancé avec ton compte (`.env`). Il récupère `.well-known/home` des services `organize`, `planning`, `shopping`, `created-recipes`, `rating`, `community/profile` et `customer-devices`. Il enregistre les rels dans `scraper/data/user/well_known.json` et un exemple de réponse JSON par endpoint dans `scraper/data/user/samples/`.
2. Trouver les rels des favoris et des notes perso. Vérifier si `user-ratings` a une version groupée.
3. Documenter les endpoints et les formats dans `scraper/REVERSE_NOTES.md` §4. Les réponses anonymisées deviennent les fixtures de test Kotlin (étape 6).

### Étape 2 — Connexion (Kotlin, `data/cookidoo/auth/`)

Flux OAuth2 *authorization code + PKCE*, identique à `cookidoo_api.login()`, mais le formulaire est affiché à l'utilisateur au lieu d'être posté par le code :

1. Récupérer la configuration OIDC : `GET https://ciam.prod.cookidoo.vorwerk-digital.com/.well-known/openid-configuration`.
2. Générer `verifier` (48 octets aléatoires, base64url), `challenge = base64url(sha256(verifier))` et un `state` aléatoire.
3. Ouvrir dans la WebView (`CookidooLoginScreen`) : `authorization_endpoint?response_type=code&client_id=mobile-android&redirect_uri=com.vorwerk.cookidoo://code-grant&market=FR&scope=openid profile email offline offline_access&state=…&code_challenge=…&code_challenge_method=S256&ui_locales=fr-FR`.
4. Dans `shouldOverrideUrlLoading`, intercepter toute URL qui commence par `com.vorwerk.cookidoo://code-grant`, vérifier `state` et extraire `code`. Bloquer toute navigation hors des domaines CIAM, Cookidoo et des fournisseurs de connexion sociale.
5. Échanger le code : `POST token_endpoint` avec `grant_type=authorization_code`, `code`, `redirect_uri`, `code_verifier` et `client_id=mobile-android`. Le token d'accès est valable 12 h, ce qui suffit pour un import.
6. Garder le token **en mémoire seulement** (ViewModel). Ensuite, vider les cookies de la WebView (`CookieManager.removeAllCookies`) pour que la session ne reste pas ouverte.

Choix techniques :
- Utiliser une WebView plutôt qu'un Custom Tab. Le redirect `com.vorwerk.cookidoo://` est enregistré par l'app Cookidoo officielle : avec un Custom Tab, si celle-ci est installée, Android lui enverrait le code. La WebView l'intercepte avant.
- Le User-Agent par défaut de la WebView passe Cloudflare. Ne pas le modifier.
- Risque : si Vorwerk restreint le client `mobile-android` à son app signée, le flux échoue. **Faire un prototype de l'étape 2 en premier** pour valider la faisabilité.

### Étape 3 — Client API (Kotlin, `data/cookidoo/api/`)

1. Ajouter OkHttp (`com.squareup.okhttp3:okhttp`, déjà présent via Coil, à déclarer explicitement dans `libs.versions.toml`). Gson existe déjà.
2. `CookidooApi` reçoit le token et l'URL de base `https://cookidoo.fr/`. Il envoie les en-têtes `Authorization: Bearer …` et `Accept: application/json`.
3. `WellKnown` : résoudre les chemins à partir des documents HAL `/{service}/.well-known/home`, comme `well_known.py`. Ne pas coder de chemin en dur : un rel introuvable donne l'erreur « Cookidoo a changé ».
4. Une méthode suspendue par ligne du §2, avec des DTO Gson calqués sur `raw_types.py`. Gérer la pagination des listes.
5. Débit limité à environ 4 req/s. Sur une 401 au milieu de l'import, arrêter avec l'erreur « Session expirée ».

### Étape 4 — Collecte, puis écriture (Kotlin, `data/cookidoo/import/`)

1. **Collecte (`CookidooCollector`)** : tout télécharger dans un objet en mémoire `CookidooSnapshot`, sans toucher à la base. L'écran Analyse affiche les compteurs de ce snapshot.
   - Ma semaine : de la semaine en cours à J+56. L'historique passé n'est pas importé.
   - Évaluations, seulement si la case est cochée : une requête par recette vue dans le snapshot (favoris, listes, Ma semaine, courses).
   - Images des créations : les télécharger avec le token dans `filesDir/images/ck_<id>.jpg`.
2. **Écriture (`CookidooImporter`)** : une seule transaction Room (`withTransaction`). Si une erreur survient, rien n'est écrit.
   - **Recettes du catalogue :** chercher l'id dans `recipes`. S'il est absent, la recette va dans « introuvables » (nom et image conservés pour le rapport) et n'est pas ajoutée aux tables.
   - **Créations :** créer une `RecipeEntity` d'id `ck_<idCookidoo>`, au format lu par `RecipeEditorScreen`. Les instructions annotées (`CookidooInstruction` : TTS, mode, température) sont aplaties en texte. Une réimportation remplace la création de même id.
   - **Listes :** les faire correspondre par nom. Si la liste existe, y ajouter les recettes. Sinon, la créer. Les ids de créations sont remappés en `ck_…`.
   - **Favoris, collections enregistrées, évaluations, notes :** upsert.
   - **Ma semaine :** pas de doublon pour un même couple (date, recette).
   - **Courses :** passer par `ShoppingLogic` pour agréger les quantités comme lors d'un ajout manuel. Un article possédé devient `checked = true`.
   - **Mode Remplacer :** effacer les tables de chaque catégorie cochée avant l'écriture. Ne jamais toucher aux catégories décochées.
3. Le résultat est un `ImportReport`, qui alimente l'écran de fin.

### Étape 5 — UI (Kotlin, `ui/importer/`)

- Ajouter dans `CoquilleDureNavigation` les routes `import/intro`, `import/login`, `import/review`, `import/progress` et `import/done`.
- `CookidooImportViewModel` (Hilt) porte le token, le snapshot et la progression. Le token disparaît avec le ViewModel.
- Le style suit l'app : boutons verts, feuilles Material3, police plafonnée à 1.3. Vérifier en petit écran et en mode sombre.
- Le bouton retour pendant l'import demande « Annuler l'import ? ». La collecte est annulable, l'écriture transactionnelle ne l'est pas.

### Étape 6 — Tests

- Tests JVM (`android/app/src/test`, à créer) :
  - parsing des DTO à partir des fixtures de l'étape 1 ;
  - `CookidooImporter` sur une DB Room en mémoire (Robolectric) : remapping des ids, idempotence (deux imports donnent le même état), mode Remplacer et introuvables ;
  - générateur PKCE (comparer à un vecteur connu).
- `MockWebServer` (OkHttp) pour `WellKnown` et la pagination.
- Test manuel sur ton compte, sur `qa_small_api26` (Android 8) et sur le téléphone : connexion, import, réimport sans doublon, puis vérification des écrans Mes recettes, Ma semaine et Courses avec `qa/shoot.py`.

## 4. Ordre de livraison

1. **Prototype de connexion** (étape 2). Il conditionne tout le reste : si le client `mobile-android` refuse la WebView, revoir l'approche avant d'aller plus loin.
2. Repérage de l'API (étape 1).
3. Client API, puis listes, favoris et Ma semaine (étapes 3 à 5, première tranche de bout en bout).
4. Courses, puis créations avec images.
5. Évaluations, notes perso, nom et appareils.

## 5. Risques

- **Restriction du client OAuth ou challenge Cloudflare dans la WebView :** c'est un point bloquant, testé en premier (§4.1).
- **Changement d'API côté Cookidoo :** résolution via `.well-known` et messages d'erreur clairs. Garder `cookidoo-api` à jour dans le venv pour suivre les changements.
- **Recettes absentes de la seed :** elles apparaissent dans le rapport. Si elles sont nombreuses, enrichir la seed (`02`, `04` puis `07`) ou importer un squelette de recette (nom et image) marqué « non disponible hors ligne ».
- **Données personnelles :** rien n'est envoyé ailleurs qu'à Cookidoo. Le token et les cookies sont effacés après l'import. Ne rien journaliser du contenu des réponses en release.
- **Conditions d'utilisation de Cookidoo :** l'import utilise le compte de l'utilisateur, en lecture, pour ses propres données. Le signaler dans les Mentions légales.

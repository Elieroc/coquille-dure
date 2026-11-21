# Cookidoo : notes, avis, versions Thermomix

Méthode : lecture du code `cookidoo_api` (auth CIAM OAuth2 déjà utilisée), puis
crawl des documents HAL `https://cookidoo.fr/{service}/.well-known/home`
(authentifié) pour lister les rels de chaque microservice. Aucune écriture
côté compte. Les requêtes passent par `api._request_json` (headers/token de la
session `cookidoo_api`).

## 1. Note moyenne + nombre d'avis

Service `rating` (rels `rating:*` du `.well-known/home`).

- Une recette : `GET https://cookidoo.fr/rating/fr-FR/aggregated-ratings/recipes/{id}`
  -> `{"aggregatedRating": 4.556, "numberOfRatings": 196, "updatedAt": "..."}`
  (l'app affiche 4.6 (196) = arrondi à 1 décimale). Non noté : 0 / 0.
- Dump groupé : `GET .../aggregated-ratings/recipes?page=N` (100 items/page,
  `_links.next` tant qu'il reste des pages ; ~3000+ pages, toutes langues,
  items non notés à 0). Les paramètres ids/limit sont ignorés.
- Aussi dans la recherche : `GET {api}/search/fr?query=...` renvoie `rating` et
  `numberOfRatings` par hit (champs ignorés par `cookidoo_api`).
- Autres rels : `user-ratings/recipes/{id}` (note de l'utilisateur, 404 si aucune),
  `partials/recipe-aggregated-rating/{id}` (fragment HTML).

## 2. Avis individuels (texte)

Non obtenable : aucun service/rel review/comment/feedback dans les documents
well-known (rating, recipes, community/profile, foundation, search, organize,
planning, shopping, created-recipes) ; l'app n'affiche que « 4.6 (196) ».
Cookidoo n'a que des notes étoilées, pas de commentaires. `reviews` reste `[]`.

## 3. Versions Thermomix compatibles

`GET {api}/recipes/recipe/fr-FR/{id}` (rel `recipe:details`) renvoie
`thermomixVersions: ["TM7","TM6","TM5","TM31"]` (champ ignoré par
`cookidoo_api`, donc absent de `data/recipes/*.json`). Valeurs observées : TM7,
TM6, TM5, TM31. Filtre de recherche équivalent : paramètre `tmv`.

## Scripts

- `04_extract_ratings.py` : phase A dump groupé -> `data/ratings_bulk.json`;
  phase B détail des recettes françaises (marqueur `vitesse`) -> `data/ratings/{id}.json`
  `{id, rating, rating_count, tm_versions, reviews}`. 2 req/s, reprenable,
  progression `data/ratings_progress.json`. Relancer après la fin de 02_extract
  pour couvrir les nouvelles recettes.
- `sample_recipes.json` enrichi (rating, rating_count, tm_versions, reviews).

## 4. Données utilisateur (repérage du 2026-11-18, lecture seule)

Script : `10_discover_user.py` (login `cookidoo_api`, GET uniquement). Sortie dans
`data/user/` : `rels.txt` (tous les rels par service), `well_known/`, `samples/`
(réponses brutes, données perso, ignorées par git). Formats confirmés sur un compte
rempli (2 favoris, 1 liste de 2 recettes, 3 recettes en Ma semaine, 3 en courses, 36
ingrédients). Non confirmés : collections enregistrées (compte à 0) et créations (voir 502).

Base : `https://cookidoo.fr`, `{lang}` = `fr-FR`. En-tête `Authorization: Bearer <token>`.
Le token du flux WebView (prototype Android) donne les mêmes accès que `cookidoo_api`.

| Donnée | Endpoint | Réponse (vérifiée) |
|---|---|---|
| Profil | `GET community/profile/{lang}` | `{id, userInfo{username,picture}, savedSearches[], foodPreferences[], thermomixes[], isPublic}` |
| Appareils TM | `GET customer-devices/api/my-devices/versions` | 204 sans corps si aucun |
| Créations | `GET created-recipes/{lang}` | `{meta{recipeLimit,…}, items[]}` ; détail `/{id}` |
| **Favoris** | `GET organize/{lang}/api/bookmark?page=N` | `{bookmarks[], listType:"BOOKMARKLIST", page{page,totalPages,totalElements}}` (rel `organize:api-bookmark`) |
| Favoris (sync) | `GET organize/api/list-sync/bookmark` puis `/{id}` | liste `bookmark-<uuid>` avec `recipes[]` |
| Mes listes | `GET organize/{lang}/api/custom-list?page=N` | `{customlists[], page{…}}` |
| Collections enregistrées | `GET organize/{lang}/api/managed-list?page=N` | `{managedlists[], page{…}}` |
| Ma semaine | `GET planning/{lang}/api/my-week/{YYYY-MM-DD}` | `{myDays[{dayKey,date,recipeIds[],customerRecipeIds[],recipes[]}], recipeCount}` |
| Ma semaine (enrichi) | `GET planning/{lang}/api/my-week-enhanced/{YYYY-MM-DD}` | `myDays[].plannedRecipes[{recipeId,mealTime,recipeType,assets…}]` |
| Courses | `GET shopping/{lang}` | `{recipes[{id,title,servingSize,descriptiveAssets…}], customerRecipes[], additionalItems[]}` |
| Historique de cuisson | `GET organize/{lang}/api/cooking-history` | `{userId, entries[]}` |
| Recherches enregistrées | `GET community/profile/{lang}/saved-searches` | `{savedSearches[]}` |
| Évaluation perso | `GET rating/{lang}/user-ratings/recipes/{recipeId}` | 404 `Could not find user rating.` si aucune ; **une requête par recette** |
| Recettes d'une collection | `GET recipes/recipe/{lang}/{id}/collections` | (rel `recipe:recipe-collections`) |

Absents : dump groupé des évaluations
(`rating/{lang}/recipes-ratings` renvoie 404), `list-sync/custom-list` et
`planning/.../list-sync/my-week` (404). Services testés en 404 : `recipes`, `bookmarks`,
`favorites`, `collections`, `cooking`, `remote-monitoring`.
Autres rels notables : `organize:api-associated-recipe-list` (`api/associated-recipes`),
`organize:api-share-custom-list`, `organize:api-cooking-history-multiple`,
`planning:api-my-day-enhanced*`.

### Formats d'items confirmés

- Favori : `bookmarks[] = {id, created, recipe{id, title, asciiTitle, prepTime, squareImage, landscapeImage, assets…}}`.
- Liste perso : `customlists[] = {id, title, created, modified, recipeCount, recipeIds[], shared, sharedListId, assets…}`.
  Les `recipeIds` sont déjà dans la réponse de la liste : pas d'appel supplémentaire.
- Ma semaine : `myDays[] = {dayKey, date, recipeIds[], customerRecipeIds[], recipes[]}` ; la version
  `my-week-enhanced` donne `plannedRecipes[{recipeId, mealTime, recipeType}]`.
- Courses : `shopping/{lang}` donne `recipes[]` (la même recette peut apparaître 2 fois = 2 ajouts, ex. `r357760`)
  et `additionalItems[]` ; les ingrédients (`get_ingredient_items`, 36 ici) sont
  `{id, name, is_owned, description (quantité), preparation}` avec `is_owned` = coché.
- Évaluation perso : 404 pour toutes les recettes testées (aucune notée sur ce compte).
- `created-recipes` : HTTP 502 sur **tout le service** (liste, `/{id}`, `device/lists`, `health-check`),
  sur `.fr`, `.ch` et `.de`, avec ou sans User-Agent, avec ou sans paramètres. Le corps est la page de
  maintenance HTML de Cookidoo (« Des ingrédients frais sont en chemin… reviendrons très prochainement »).
  Seul `created-recipes/.well-known/home` répond 200 (servi par la passerelle). Ce n'est donc ni un bug
  du compte ni de `cookidoo_api` : panne côté Cookidoo, constatée le 2026-11-18. À retester plus tard.
  L'import doit traiter toute réponse non-JSON ou 5xx de ce service comme « créations indisponibles »,
  continuer le reste et l'indiquer dans le rapport.

### Créations : appels API identifiés (service en panne, non vérifiables)

Document `https://cookidoo.fr/.well-known/mobile-home` (celui de l'app mobile) : les services
sont aussi exposés sur `https://fr.tmmobile.vorwerk-digital.com/{service}/…` (app mobile) et
`https://fr.web.production-eu.cookidoo.vorwerk-digital.com/{service}/…` (web). `created-recipes`
répond **502 sur les trois hôtes**, y compris `/.well-known/home` : panne d'origine, pas de contournement.

Rels du service `created-recipes` (extraits de son `.well-known/home`, 2026-11-18) :

| Rel | Chemin |
|---|---|
| `recipes-list` / `recipe-create` | `GET created-recipes/{lang}` (liste ; `?ids=`), en-tête `Accept: application/vnd.vorwerk.customer-recipe.full+json` (valeur de `cookidoo_api`) |
| `recipe-details` | `GET created-recipes/{lang}/{id}` |
| `recipe-details-public` | `GET created-recipes/public/recipes/{lang}/{id}` |
| `device-offline-recipes` | `GET created-recipes/{lang}/device/offline-recipes` (liste pour appareils) |
| `device-recipe-details` | `GET created-recipes/{lang}/device/recipes/{recipe_id}` |
| `device-lists` / `device-list-details` | `GET created-recipes/{lang}/device/lists/{type}[/{id}]` |
| `recipes-download`, `config`, `image-signature` | `…/{lang}/download`, `…/config`, `…/image/signature` |

Aucune autre voie : `organize/api/list-sync/{created,customer-recipes,…}` 404, `list-sync/customlist`
200 mais ce sont les listes perso ; `search?context=created` renvoie le catalogue. Les créations ne
sont référencées ailleurs que par id (`planning … customerRecipeIds`, `shopping … customerRecipes`).
Le code Kotlin (`CookidooApi.creations()`) utilise déjà `recipes-list` ; à valider quand le service revient.

### Notes perso : elles existent (correction)

Service `recipe-notes` (200 sur `cookidoo.fr`) :
`GET recipe-notes/{lang}/recipes/{recipeId}` (204 sans corps si aucune note, testé sur 8 recettes),
`POST recipe-notes/{lang}/recipes` (création, non appelé). Pas de liste : une requête par recette.
Autre service du mobile : `ri-api-gateway` (`/api/v1/import`, `/api/translations`, `/api/plant-based`).
Format d'une note (réponse 200) non vérifié, aucune note sur le compte de test.

### App Cookidoo officielle (Android 26.10.11, observée le 2026-11-18 sur le téléphone, adb sans fil)

- L'app affiche « Mes créations : 0 Recette » puis l'écran vide « Ajouter vos propres recettes » (pas de message d'erreur),
  alors que `GET created-recipes/fr-FR` renvoie 502 pour nous. Les deux cas sont compatibles avec « aucune création »
  (le tout premier appel, avant l'ajout de données, avait renvoyé 200 `{"meta":…, "items":[]}`) ; l'app peut aussi
  masquer l'erreur. À trancher : demander à l'utilisateur s'il a réellement des créations dans Cookidoo.
- Analyse statique de l'APK (strings des `classes*.dex`, client Ktor/OkHttp, Realm local) :
  - hôte mobile `https://%s.tmmobile.vorwerk-digital.com`, découverte par `/.well-known/mobile-home` (rel `tmde2:customer-recipes`) ;
  - en-têtes `Accept` propres au mobile : `application/vnd.vorwerk.customer-recipe.full+json` (créations),
    `…organize.bookmark.mobile+json`, `…organize.custom-list.mobile+json`, `…organize.managed-list.mobile+json`,
    `…planning.my-day.mobile+json`, `…planning.my-day-enhanced.mobile+json`, `…recipe.mobile.v1+json` ;
  - rels utilisés pour les créations : `customer-recipes:recipe-create` (liste), `recipe-details`, `recipes-list`,
    `config`, `image-signature`, `add-to-cookidoo`, `import-options`, `scale-recipe-page`, `mobile-share-recipe` ;
  - détail, édition et mise à l'échelle d'une création sont des **WebViews** (pages serveur), pas des JSON ;
  - la liste passe par `CustomerRecipesSyncWorker` / `MyRecipesSyncWorker` et un DTO `CustomerRecipesCollectionDto` ;
  - notes perso : `RecipeNoteDto` / `UpdateNoteDto`, service `recipe-notes` confirmé.
- Pas de capture réseau : le trafic est en TLS et aucun proxy d'interception n'a été installé.

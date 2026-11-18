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

Absents : notes perso sur une recette (aucun rel `note`), dump groupé des évaluations
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
- `GET created-recipes/fr-FR` : HTTP 502 constant le 2026-11-18, aussi via `cookidoo_api.list_custom_recipes()`
  (il répondait 200 avant l'ajout de données). Panne côté Cookidoo ou bug du service : prévoir une erreur
  tolérée (l'import continue sans les créations) et retester.

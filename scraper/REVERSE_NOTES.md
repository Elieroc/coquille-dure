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

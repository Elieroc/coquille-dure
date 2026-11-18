"""Phase 4: notes (rating) + versions Thermomix (TM5/TM6/TM7) des recettes.

A) Dump groupé des notes : GET rating/fr-FR/aggregated-ratings/recipes?page=N
   (100 recettes par page, jusqu'à page vide) -> data/ratings_bulk.json
B) Versions TM + catégorie courses des ingrédients : GET recipes/recipe/fr-FR/{id}
   (champs thermomixVersions, ingredients[].shoppingCategory_ref) pour
   chaque recette française déjà extraite (data/recipes, marqueur 'vitesse')
   -> data/ratings/{id}.json
Les avis individuels (texte) n'existent pas côté Cookidoo : reviews = [].
Reprenable, <= 2 req/s, progression dans data/ratings_progress.json.
"""

import asyncio
import json
import os
from pathlib import Path

import aiohttp
from yarl import URL
from cookidoo_api import Cookidoo, CookidooConfig, CookidooLocalizationConfig

DATA = Path(__file__).parent / "data"
RECIPES_DIR = DATA / "recipes"
RATINGS_DIR = DATA / "ratings"
BULK_FILE = DATA / "ratings_bulk.json"
PROGRESS_FILE = DATA / "ratings_progress.json"
DELAY = 0.5  # 2 req/s max
RATING_URL = URL("https://cookidoo.fr/rating/fr-FR/aggregated-ratings/recipes")


def load_json(path: Path, default):
    if path.exists():
        with open(path) as f:
            return json.load(f)
    return default


def save_json(path: Path, obj):
    tmp = path.with_suffix(".tmp")
    with open(tmp, "w") as f:
        json.dump(obj, f, ensure_ascii=False)
    tmp.replace(path)


def is_french(path: Path) -> bool:
    try:
        with open(path) as f:
            return "vitesse" in f.read()
    except OSError:
        return False


async def run():
    email = os.environ.get("COOKIDOO_EMAIL") or ""
    password = os.environ.get("COOKIDOO_PASS") or ""
    if not email or not password:
        raise SystemExit("Set COOKIDOO_EMAIL and COOKIDOO_PASS env vars")

    RATINGS_DIR.mkdir(parents=True, exist_ok=True)
    bulk = load_json(BULK_FILE, {"next_page": 0, "done": False, "ratings": {}})
    prog = {"phase": "A", "bulk_pages": bulk["next_page"], "tm_done": 0, "tm_total": 0, "errors": 0}

    def write_progress():
        save_json(PROGRESS_FILE, prog)

    loc = CookidooLocalizationConfig(
        country_code="fr", language="fr-FR", url="https://cookidoo.fr/foundation/fr-FR"
    )
    cfg = CookidooConfig(email=email, password=password, localization=loc)

    async with aiohttp.ClientSession() as session:
        api = Cookidoo(session, cfg)
        await api.login()
        await api._ensure_endpoints()
        requests = 0

        async def relogin_if_needed():
            nonlocal requests
            requests += 1
            if requests % 500 == 0:
                try:
                    await api.login()
                except Exception:
                    pass

        # Phase A
        while not bulk["done"]:
            page = bulk["next_page"]
            try:
                r = await api._request_json(
                    "get", RATING_URL, "ratings page", params={"page": str(page)}
                )
            except Exception as e:
                prog["errors"] += 1
                print(f"  ERROR page {page}: {type(e).__name__}: {e}")
                await asyncio.sleep(5)
                await api.login()
                continue
            items = (r or {}).get("items", [])
            for it in items:
                if it.get("numberOfRatings"):
                    bulk["ratings"][it["id"]] = [
                        round(it["aggregatedRating"], 1),
                        it["numberOfRatings"],
                    ]
            bulk["next_page"] = page + 1
            if not items or not (r or {}).get("_links", {}).get("next"):
                bulk["done"] = True
            prog["bulk_pages"] = bulk["next_page"]
            if page % 20 == 0 or bulk["done"]:
                save_json(BULK_FILE, bulk)
                write_progress()
                print(f"  ratings page {page}: {len(bulk['ratings'])} rated recipes")
            await relogin_if_needed()
            await asyncio.sleep(DELAY)
        save_json(BULK_FILE, bulk)

        # Phase B (rescan jusqu'à plus de nouvelles recettes françaises)
        prog["phase"] = "B"
        while True:
            todo = [
                p.stem
                for p in sorted(RECIPES_DIR.glob("*.json"))
                if not (RATINGS_DIR / f"{p.stem}.json").exists() and is_french(p)
            ]
            done_already = len(list(RATINGS_DIR.glob("*.json")))
            prog["tm_total"] = done_already + len(todo)
            if not todo:
                break
            for rid in todo:
                try:
                    url = api.api_endpoint / api._path("recipe:details").format(
                        language="fr-FR", id=rid
                    )
                    raw = await api._request_json("get", url, "recipe tm versions")
                    tm = list((raw or {}).get("thermomixVersions") or [])
                    rating, count = bulk["ratings"].get(rid, [None, 0])
                    ingredients = [
                        {
                            "id": (ing.get("id") or "").rsplit("-", 1)[-1],
                            "cat": (ing.get("shoppingCategory_ref") or "").rsplit("-", 1)[-1],
                            "icon": bool(ing.get("icon")),
                        }
                        for ing in (raw or {}).get("ingredients") or []
                    ]
                    save_json(
                        RATINGS_DIR / f"{rid}.json",
                        {
                            "id": rid,
                            "rating": rating,
                            "rating_count": count,
                            "tm_versions": tm,
                            "reviews": [],
                            "ingredients": ingredients,
                        },
                    )
                    prog["tm_done"] = len(list(RATINGS_DIR.glob("*.json"))) if prog["tm_done"] % 50 == 0 else prog["tm_done"] + 1
                except Exception as e:
                    prog["errors"] += 1
                    print(f"  ERROR {rid}: {type(e).__name__}: {e}")
                if prog["tm_done"] % 50 == 0:
                    write_progress()
                await relogin_if_needed()
                await asyncio.sleep(DELAY)
            write_progress()

        prog["phase"] = "done"
        write_progress()
        print(f"Done: {prog}")


if __name__ == "__main__":
    asyncio.run(run())

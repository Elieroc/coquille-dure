"""Phase 6: détails complémentaires des recettes françaises.

GET {api}/recipes/recipe/fr-FR/{id} -> data/details/{id}.json avec :
tags, countries (targetCountries), devices (optionalDevices + additionalDevices),
variants (variantCluster), collections (inCollections avec couverture), ingredient icons.
Périmètre : recettes présentes dans data/ratings/ (= françaises). Reprenable,
<= 2 req/s. Identifiants : COOKIDOO_EMAIL / COOKIDOO_PASS (variables d'environnement).
"""

import asyncio
import json
import os
from pathlib import Path

import aiohttp
from cookidoo_api import Cookidoo, CookidooConfig, CookidooLocalizationConfig

DATA = Path(__file__).parent / "data"
RATINGS_DIR = DATA / "ratings"
DETAILS_DIR = DATA / "details"
PROGRESS_FILE = DATA / "details_progress.json"
DELAY = 0.5


def save_json(path: Path, obj):
    tmp = path.with_suffix(".tmp")
    with open(tmp, "w") as f:
        json.dump(obj, f, ensure_ascii=False)
    tmp.replace(path)


def compact(rid: str, raw: dict) -> dict:
    def cover(c):
        for a in c.get("descriptiveAssets") or []:
            if a.get("square"):
                return a["square"]
        return ""

    devices = []
    for key in ("optionalDevices", "additionalDevices"):
        for d in raw.get(key) or []:
            devices.append(d if isinstance(d, (str, dict)) else str(d))
    icons = {}
    for g in raw.get("recipeIngredientGroups") or []:
        for i in g.get("recipeIngredients") or []:
            n = (i.get("localId") or "").rsplit("-", 1)[-1]
            if n and i.get("icon"):
                icons[n] = i["icon"]
    return {
        "id": rid,
        "tags": [{"id": t.get("id"), "name": t.get("name")} for t in raw.get("tags") or []],
        "countries": raw.get("targetCountries") or [],
        "devices": devices,
        "variants": raw.get("variantCluster") or [],
        "veganizable": bool(raw.get("veganizable")),
        "collections": [
            {
                "id": c.get("id"),
                "title": c.get("title"),
                "count": (c.get("recipesCount") or {}).get("value", 0),
                "market": c.get("market"),
                "cover": cover(c),
            }
            for c in raw.get("inCollections") or []
        ],
        "icons": icons,
    }


async def run():
    email = os.environ.get("COOKIDOO_EMAIL") or ""
    password = os.environ.get("COOKIDOO_PASS") or ""
    if not email or not password:
        raise SystemExit("Set COOKIDOO_EMAIL and COOKIDOO_PASS env vars")
    DETAILS_DIR.mkdir(parents=True, exist_ok=True)
    loc = CookidooLocalizationConfig(country_code="fr", language="fr-FR", url="https://cookidoo.fr/foundation/fr-FR")
    cfg = CookidooConfig(email=email, password=password, localization=loc)
    ids = sorted(p.stem for p in RATINGS_DIR.glob("*.json"))
    todo = [i for i in ids if not (DETAILS_DIR / f"{i}.json").exists()]
    prog = {"total": len(ids), "done": len(ids) - len(todo), "errors": 0}
    async with aiohttp.ClientSession() as session:
        api = Cookidoo(session, cfg)
        await api.login()
        await api._ensure_endpoints()
        for n, rid in enumerate(todo, 1):
            try:
                url = api.api_endpoint / api._path("recipe:details").format(language="fr-FR", id=rid)
                raw = await api._request_json("get", url, "recipe details")
                save_json(DETAILS_DIR / f"{rid}.json", compact(rid, raw or {}))
                prog["done"] += 1
            except Exception as e:
                prog["errors"] += 1
                print(f"  ERROR {rid}: {type(e).__name__}: {e}")
                await asyncio.sleep(5)
                try:
                    await api.login()
                except Exception:
                    pass
            if n % 50 == 0:
                save_json(PROGRESS_FILE, prog)
            if n % 500 == 0:
                try:
                    await api.login()
                except Exception:
                    pass
            await asyncio.sleep(DELAY)
    save_json(PROGRESS_FILE, prog)
    print("Done", prog)


if __name__ == "__main__":
    asyncio.run(run())

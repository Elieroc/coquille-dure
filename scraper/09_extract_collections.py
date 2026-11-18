"""Phase 9: descriptions des collections.

GET https://cookidoo.fr/collection/fr-FR/{id} -> {"collection": {"id", "title", "description", "recipes": [...]}}
Entrée : ids de collections dans data/details/*.json (champ collections[].id).
Sortie : data/collections/{id}.json = {"id", "title", "description", "recipeIds"}.
Reprenable (saute les fichiers existants). Identifiants : COOKIDOO_EMAIL / COOKIDOO_PASS.
"""

import asyncio
import json
import os
from pathlib import Path

import aiohttp
from cookidoo_api import Cookidoo, CookidooConfig, CookidooLocalizationConfig

DATA = Path(__file__).parent / "data"
DETAILS_DIR = DATA / "details"
OUT_DIR = DATA / "collections"
DELAY = 0.7


def save_json(path: Path, obj):
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(obj, ensure_ascii=False))
    tmp.replace(path)


def collection_ids() -> list[str]:
    ids: set[str] = set()
    for p in DETAILS_DIR.glob("*.json"):
        try:
            ids.update(c["id"] for c in json.loads(p.read_text()).get("collections", []))
        except (OSError, ValueError, KeyError):
            pass
    return sorted(ids)


async def run():
    email, password = os.environ.get("COOKIDOO_EMAIL"), os.environ.get("COOKIDOO_PASS")
    if not email or not password:
        raise SystemExit("Set COOKIDOO_EMAIL and COOKIDOO_PASS env vars")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    loc = CookidooLocalizationConfig(country_code="fr", language="fr-FR", url="https://cookidoo.fr/foundation/fr-FR")
    todo = [i for i in collection_ids() if not (OUT_DIR / f"{i}.json").exists()]
    print(f"{len(todo)} collections à traiter")
    async with aiohttp.ClientSession() as session:
        api = Cookidoo(session, CookidooConfig(email=email, password=password, localization=loc))
        await api.login()
        await api._ensure_endpoints()
        for n, cid in enumerate(todo, 1):
            try:
                r = await api._request_json("get", api.api_endpoint / f"collection/fr-FR/{cid}", "collection")
                c = (r or {}).get("collection", {})
                save_json(OUT_DIR / f"{cid}.json", {
                    "id": cid,
                    "title": c.get("title", ""),
                    "description": (c.get("description") or "").replace("\r\n", "\n").strip(),
                    "recipeIds": [x["id"] for x in c.get("recipes", []) if "id" in x],
                })
                await asyncio.sleep(DELAY)
            except Exception as e:
                print(f"  ERROR {cid}: {type(e).__name__}: {e}")
                await asyncio.sleep(5)
                try:
                    await api.login()
                except Exception:
                    await asyncio.sleep(30)
            if n % 100 == 0:
                print(f"  {n}/{len(todo)}")
    print("Done")


if __name__ == "__main__":
    asyncio.run(run())

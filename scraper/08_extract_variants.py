"""Phase 8: variantes de portions des recettes françaises.

GET {api}/recipes/cluster/{uid} -> [{recipe_id, quantity, notation, types}]
Entrée : data/details/{id}.json (champ variants[].uid, produit par 06).
Sortie : data/variants/{id}.json = [{"id": recipe_id, "servings": int}] (vide si recette sans variante).
Reprenable, ~1,4 req/s. Boucle tant que 06 n'a pas fini d'écrire data/details/.
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
OUT_DIR = DATA / "variants"
DELAY = 0.7


def save_json(path: Path, obj):
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(obj, ensure_ascii=False))
    tmp.replace(path)


async def run():
    email, password = os.environ.get("COOKIDOO_EMAIL"), os.environ.get("COOKIDOO_PASS")
    if not email or not password:
        raise SystemExit("Set COOKIDOO_EMAIL and COOKIDOO_PASS env vars")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    loc = CookidooLocalizationConfig(country_code="fr", language="fr-FR", url="https://cookidoo.fr/foundation/fr-FR")
    cfg = CookidooConfig(email=email, password=password, localization=loc)
    total = len(list(RATINGS_DIR.glob("*.json")))
    async with aiohttp.ClientSession() as session:
        api = Cookidoo(session, cfg)
        await api.login()
        await api._ensure_endpoints()
        n = 0
        while True:
            todo = [p.stem for p in sorted(DETAILS_DIR.glob("*.json")) if not (OUT_DIR / f"{p.stem}.json").exists()]
            if not todo:
                if len(list(DETAILS_DIR.glob("*.json"))) >= total:
                    break
                await asyncio.sleep(120)
                continue
            for rid in todo:
                try:
                    d = json.loads((DETAILS_DIR / f"{rid}.json").read_text())
                    uid = next((v.get("uid") for v in d.get("variants", []) if v.get("clusterType") == "portion"), None)
                    out = []
                    if uid:
                        r = await api._request_json("get", api.api_endpoint / f"recipes/cluster/{uid}", "cluster")
                        out = [
                            {"id": x["recipe_id"], "servings": int(x["quantity"])}
                            for x in (r or [])
                            if "portion" in (x.get("types") or []) and str(x.get("quantity", "")).isdigit()
                        ]
                        await asyncio.sleep(DELAY)
                    save_json(OUT_DIR / f"{rid}.json", out if len(out) > 1 else [])
                except Exception as e:
                    print(f"  ERROR {rid}: {type(e).__name__}: {e}")
                    await asyncio.sleep(5)
                    try:
                        await api.login()
                    except Exception:
                        pass
                n += 1
                if n % 500 == 0:
                    try:
                        await api.login()
                    except Exception as e:
                        print(f"  relogin failed: {e}")
                        await asyncio.sleep(30)
    print("Done")


if __name__ == "__main__":
    asyncio.run(run())

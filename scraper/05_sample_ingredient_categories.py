"""Ajoute la catégorie de courses Cookidoo (shopping_category = n° rpf) à chaque
ingrédient de android/app/src/main/res/raw/sample_recipes.json.
Source : GET recipes/recipe/fr-FR/{id} -> ingredients[].shoppingCategory_ref. 1 req/s.
"""
import asyncio, json, os
from pathlib import Path
import aiohttp
from cookidoo_api import Cookidoo, CookidooConfig, CookidooLocalizationConfig

SAMPLE = Path(__file__).parent.parent / "android/app/src/main/res/raw/sample_recipes.json"


async def main():
    loc = CookidooLocalizationConfig(country_code="fr", language="fr-FR", url="https://cookidoo.fr/foundation/fr-FR")
    cfg = CookidooConfig(email=os.environ["COOKIDOO_EMAIL"], password=os.environ["COOKIDOO_PASS"], localization=loc)
    recipes = json.loads(SAMPLE.read_text())
    async with aiohttp.ClientSession() as s:
        api = Cookidoo(s, cfg)
        await api.login()
        await api._ensure_endpoints()
        for i, r in enumerate(recipes):
            url = api.api_endpoint / api._path("recipe:details").format(language="fr-FR", id=r["id"])
            try:
                raw = await api._request_json("get", url, "ingredient categories")
            except Exception as e:
                print("ERROR", r["id"], e)
                continue
            cats = {
                (ing.get("id") or ""): (ing.get("shoppingCategory_ref") or "").rsplit("-", 1)[-1]
                for ing in raw.get("ingredients") or []
            }
            for ing in r["ingredients"]:
                c = cats.get(ing["id"])
                ing["shopping_category"] = int(c) if c and c.isdigit() else None
            if i % 20 == 0:
                print(i, r["id"])
            await asyncio.sleep(1.0)
    SAMPLE.write_text(json.dumps(recipes, ensure_ascii=False))


asyncio.run(main())

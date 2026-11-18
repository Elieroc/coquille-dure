"""Phase 10: repérage des endpoints de données utilisateur (lecture seule, GET uniquement).

1) Télécharge /{service}/.well-known/home (HAL) de chaque microservice -> data/user/well_known/{service}.json
2) Résume tous les rels par service -> data/user/rels.txt (à lire pour trouver favoris, notes, etc.)
3) Appelle les endpoints utilisateur connus et sauvegarde les réponses brutes -> data/user/samples/*.json
   (données personnelles : data/ est ignoré par git)
Identifiants : COOKIDOO_EMAIL / COOKIDOO_PASS, ou .env (user= / password=).
"""

import asyncio
import datetime as dt
import json
import os
from pathlib import Path

import aiohttp
from yarl import URL
from cookidoo_api import Cookidoo, CookidooConfig, CookidooLocalizationConfig
from cookidoo_api.const import DEFAULT_API_HEADERS, LOGIN_HEADERS

OUT = Path(__file__).parent / "data" / "user"
BASE = "https://cookidoo.fr"
LANG = "fr-FR"
SERVICES = [
    "organize", "planning", "shopping", "created-recipes", "rating", "community/profile",
    "customer-devices", "recipes/recipe", "ownership", "foundation", "search", "recipes",
    "bookmarks", "favorites", "collections", "profile", "cooking", "remote-monitoring",
]


def creds():
    email, pw = os.environ.get("COOKIDOO_EMAIL"), os.environ.get("COOKIDOO_PASS")
    env = Path(__file__).parent.parent / ".env"
    if (not email or not pw) and env.exists():
        kv = dict(l.rstrip("\n").split("=", 1) for l in env.read_text().splitlines() if "=" in l)
        email, pw = kv.get("user"), kv.get("password")
    if not email or not pw:
        raise SystemExit("Set COOKIDOO_EMAIL / COOKIDOO_PASS or .env")
    return email, pw


def save(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=1) if not isinstance(data, str) else data)


def rels(doc):
    links = doc.get("_links", {}) if isinstance(doc, dict) else {}
    out = []
    for k, v in links.items():
        vs = v if isinstance(v, list) else [v]
        for x in vs:
            if isinstance(x, dict) and "href" in x:
                out.append(f"{k}  ->  {x['href']}")
    return out


async def main():
    email, pw = creds()
    loc = CookidooLocalizationConfig(country_code="fr", language=LANG, url=f"{BASE}/foundation/{LANG}")
    async with aiohttp.ClientSession(cookie_jar=aiohttp.CookieJar(unsafe=True)) as s:
        api = Cookidoo(s, CookidooConfig(email=email, password=pw, localization=loc))
        await api.login()
        await api._ensure_endpoints()
        print("login OK")

        async def get(url: str, op: str):
            try:
                await api._ensure_token()
                async with s.get(URL(url, encoded=False), headers={**api._api_headers, **LOGIN_HEADERS}) as r:
                    txt = await r.text()
                    try:
                        body = json.loads(txt)
                    except ValueError:
                        body = txt[:2000]
                    return r.status, body
            except Exception as e:
                return -1, str(e)

        # 1+2 well-known
        lines = []
        for svc in SERVICES:
            st, doc = await get(f"{BASE}/{svc}/.well-known/home", f"wk {svc}")
            lines.append(f"== {svc}  [{st}]")
            if st == 200:
                save(OUT / "well_known" / (svc.replace("/", "_") + ".json"), doc)
                lines += ["  " + r for r in rels(doc)]
            await asyncio.sleep(0.3)
        save(OUT / "rels.txt", "\n".join(lines) + "\n")
        print(f"{len(lines)} lignes -> data/user/rels.txt")

        # 3 endpoints utilisateur connus
        today = dt.date.today()
        monday = today - dt.timedelta(days=today.weekday())
        known = {
            "profile": f"{BASE}/community/profile/{LANG}",
            "devices": f"{BASE}/customer-devices/api/my-devices/versions",
            "custom_recipes": f"{BASE}/created-recipes/{LANG}",
            "custom_lists": f"{BASE}/organize/{LANG}/api/custom-list",
            "managed_lists": f"{BASE}/organize/{LANG}/api/managed-list",
            "shopping": f"{BASE}/shopping/{LANG}",
            "week": f"{BASE}/planning/{LANG}/api/my-week/{monday.isoformat()}",
        }
        known.update({
            "bookmark": f"{BASE}/organize/{LANG}/api/bookmark",
            "cooking_history": f"{BASE}/organize/{LANG}/api/cooking-history",
            "list_sync_all_custom": f"{BASE}/organize/api/list-sync/custom-list",
            "list_sync_all_bookmark": f"{BASE}/organize/api/list-sync/bookmark",
            "recipes_ratings": f"{BASE}/rating/{LANG}/recipes-ratings",
            "saved_searches": f"{BASE}/community/profile/{LANG}/saved-searches",
            "week_enhanced": f"{BASE}/planning/{LANG}/api/my-week-enhanced/{monday.isoformat()}",
            "planning_list_sync_all": f"{BASE}/planning/{LANG}/api/list-sync/my-week",
        })
        for name, url in known.items():
            st, body = await get(url, name)
            save(OUT / "samples" / f"{name}.json", body)
            n = len(body) if isinstance(body, (list, dict)) else 0
            print(f"{name:16} HTTP {st}  ({type(body).__name__}, {n} éléments)")
            await asyncio.sleep(0.3)


asyncio.run(main())

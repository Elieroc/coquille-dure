"""Phase 1: Build inventory of all French recipe IDs from Cookidoo.

Strategy: search by each letter a-z, paginating up to 1000 results per letter.
If a letter saturates (1000 results), expand to 2-letter combos (aa, ab, ...).
Saves progress incrementally to data/index.json.
"""

import asyncio
import json
import os
import string
import time
from pathlib import Path

import aiohttp
from cookidoo_api import Cookidoo, CookidooConfig, CookidooLocalizationConfig

DATA_DIR = Path(__file__).parent / "data"
INDEX_FILE = DATA_DIR / "index.json"
PAGE_SIZE = 20
MAX_PAGES = 50  # 50 * 20 = 1000, the API ceiling per query
DELAY_BETWEEN_QUERIES = 1.0  # seconds between search queries


def load_index() -> dict:
    if INDEX_FILE.exists():
        with open(INDEX_FILE) as f:
            return json.load(f)
    return {"recipes": {}, "completed_queries": [], "stats": {}}


def save_index(index: dict):
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    with open(INDEX_FILE, "w") as f:
        json.dump(index, f, ensure_ascii=False, indent=2)


async def search_all_pages(api: Cookidoo, query: str | None) -> list[dict]:
    """Paginate through all results for a search query. Returns list of {id, name, thumbnail, image, url}."""
    hits = []
    for page in range(MAX_PAGES):
        results = await api.search_recipes(query=query, page=page, page_size=PAGE_SIZE)
        if not results.recipes:
            break
        for r in results.recipes:
            hits.append({
                "id": r.id,
                "name": r.name,
                "thumbnail": r.thumbnail,
                "image": r.image,
                "url": r.url,
            })
        if len(results.recipes) < PAGE_SIZE:
            break
    return hits


async def run_inventory():
    email = os.environ.get("COOKIDOO_EMAIL") or ""
    password = os.environ.get("COOKIDOO_PASS") or ""
    if not email or not password:
        raise SystemExit("Set COOKIDOO_EMAIL and COOKIDOO_PASS env vars")

    loc = CookidooLocalizationConfig(
        country_code="fr", language="fr-FR",
        url="https://cookidoo.fr/foundation/fr-FR",
    )
    cfg = CookidooConfig(email=email, password=password, localization=loc)

    index = load_index()
    all_ids = set(index["recipes"].keys())
    completed = set(index["completed_queries"])

    # Build query list: None (empty), then single letters, then 2-letter combos for saturated letters
    queries: list[str | None] = [None]
    queries.extend(list(string.ascii_lowercase))

    async with aiohttp.ClientSession() as session:
        api = Cookidoo(session, cfg)
        await api.login()
        print(f"Logged in. Starting with {len(all_ids)} known recipes.")

        saturated_letters: list[str] = []

        for q in queries:
            q_label = q or "(empty)"
            if q_label in completed:
                print(f"  '{q_label}' already done, skipping")
                continue

            hits = await search_all_pages(api, q)
            new_count = 0
            for h in hits:
                if h["id"] not in index["recipes"]:
                    index["recipes"][h["id"]] = h
                    new_count += 1
            all_ids.update(h["id"] for h in hits)

            is_saturated = len(hits) >= MAX_PAGES * PAGE_SIZE
            if is_saturated and q is not None and len(q) == 1:
                saturated_letters.append(q)

            completed.add(q_label)
            index["completed_queries"] = list(completed)
            save_index(index)

            print(f"  '{q_label}': {len(hits)} results, {new_count} new, total: {len(index['recipes'])}{'  [SATURATED]' if is_saturated else ''}")
            await asyncio.sleep(DELAY_BETWEEN_QUERIES)

        # Phase 2: expand saturated letters to 2-letter combos
        if saturated_letters:
            print(f"\nExpanding saturated letters: {saturated_letters}")
            for letter in saturated_letters:
                for second in string.ascii_lowercase:
                    q2 = letter + second
                    if q2 in completed:
                        continue

                    hits = await search_all_pages(api, q2)
                    new_count = 0
                    for h in hits:
                        if h["id"] not in index["recipes"]:
                            index["recipes"][h["id"]] = h
                            new_count += 1

                    completed.add(q2)
                    index["completed_queries"] = list(completed)
                    save_index(index)

                    sat = " [SAT]" if len(hits) >= MAX_PAGES * PAGE_SIZE else ""
                    print(f"  '{q2}': {len(hits)} results, {new_count} new, total: {len(index['recipes'])}{sat}")
                    await asyncio.sleep(DELAY_BETWEEN_QUERIES)

    index["stats"]["last_run"] = time.strftime("%Y-%m-%dT%H:%M:%S")
    index["stats"]["total_recipes"] = len(index["recipes"])
    save_index(index)
    print(f"\nInventory complete: {len(index['recipes'])} unique recipes")


if __name__ == "__main__":
    asyncio.run(run_inventory())

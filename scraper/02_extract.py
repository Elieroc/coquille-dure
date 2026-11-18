"""Phase 2: Fetch full recipe details for every ID in the inventory.

Reads data/index.json, fetches details via cookidoo-api, saves each as
data/recipes/{id}.json. Tracks progress so it can resume after interruption.
"""

import asyncio
import json
import os
import time
from dataclasses import asdict
from pathlib import Path

import aiohttp
from cookidoo_api import Cookidoo, CookidooConfig, CookidooLocalizationConfig

DATA_DIR = Path(__file__).parent / "data"
INDEX_FILE = DATA_DIR / "index.json"
RECIPES_DIR = DATA_DIR / "recipes"
PROGRESS_FILE = DATA_DIR / "extract_progress.json"
DELAY_BETWEEN_REQUESTS = 0.11  # seconds between detail fetches (~9 req/s)
BATCH_SIZE = 50  # save progress every N recipes


def load_progress() -> set[str]:
    if PROGRESS_FILE.exists():
        with open(PROGRESS_FILE) as f:
            return set(json.load(f).get("completed", []))
    return set()


def save_progress(completed: set[str]):
    with open(PROGRESS_FILE, "w") as f:
        json.dump({"completed": list(completed), "count": len(completed)}, f)


def serialize(obj):
    """Recursively convert dataclass instances to dicts."""
    if hasattr(obj, "__dataclass_fields__"):
        return {k: serialize(v) for k, v in asdict(obj).items()}
    if isinstance(obj, list):
        return [serialize(item) for item in obj]
    if isinstance(obj, dict):
        return {k: serialize(v) for k, v in obj.items()}
    return obj


async def run_extract():
    email = os.environ.get("COOKIDOO_EMAIL") or ""
    password = os.environ.get("COOKIDOO_PASS") or ""
    if not email or not password:
        raise SystemExit("Set COOKIDOO_EMAIL and COOKIDOO_PASS env vars")

    if not INDEX_FILE.exists():
        raise SystemExit("Run 01_inventory.py first")

    with open(INDEX_FILE) as f:
        index = json.load(f)

    all_ids = list(index["recipes"].keys())
    print(f"Inventory has {len(all_ids)} recipes")

    RECIPES_DIR.mkdir(parents=True, exist_ok=True)
    completed = load_progress()
    remaining = [rid for rid in all_ids if rid not in completed]
    print(f"Already extracted: {len(completed)}, remaining: {len(remaining)}")

    if not remaining:
        print("All recipes already extracted.")
        return

    loc = CookidooLocalizationConfig(
        country_code="fr", language="fr-FR",
        url="https://cookidoo.fr/foundation/fr-FR",
    )
    cfg = CookidooConfig(email=email, password=password, localization=loc)

    async with aiohttp.ClientSession() as session:
        api = Cookidoo(session, cfg)
        await api.login()
        print("Logged in. Starting extraction...")

        errors = []
        for i, rid in enumerate(remaining):
            try:
                detail = await api.get_recipe_details(rid)
                recipe_data = serialize(detail)
                # Add search metadata from index
                recipe_data["_meta"] = index["recipes"].get(rid, {})

                recipe_file = RECIPES_DIR / f"{rid}.json"
                with open(recipe_file, "w") as f:
                    json.dump(recipe_data, f, ensure_ascii=False, indent=2)

                completed.add(rid)

                if (i + 1) % BATCH_SIZE == 0:
                    save_progress(completed)

                elapsed_pct = (len(completed) / len(all_ids)) * 100
                print(f"  [{len(completed)}/{len(all_ids)}] ({elapsed_pct:.1f}%) {detail.name}")

            except Exception as e:
                errors.append({"id": rid, "error": str(e)})
                print(f"  ERROR {rid}: {type(e).__name__}: {e}")

            await asyncio.sleep(DELAY_BETWEEN_REQUESTS)

            # Re-login every 500 recipes to avoid token expiry
            if (i + 1) % 500 == 0:
                try:
                    await api.login()
                    print("  (re-authenticated)")
                except Exception:
                    pass

    save_progress(completed)

    if errors:
        errors_file = DATA_DIR / "extract_errors.json"
        with open(errors_file, "w") as f:
            json.dump(errors, f, ensure_ascii=False, indent=2)
        print(f"\n{len(errors)} errors saved to {errors_file}")

    print(f"\nExtraction complete: {len(completed)}/{len(all_ids)} recipes")


if __name__ == "__main__":
    asyncio.run(run_extract())

"""Phase 3: Download recipe images and convert to WebP.

Reads data/recipes/*.json, downloads images from Cookidoo CDN,
converts to WebP (quality 80), saves to data/images/{id}.webp.
"""

import asyncio
import json
import os
import subprocess
from pathlib import Path

import aiohttp

DATA_DIR = Path(__file__).parent / "data"
RECIPES_DIR = DATA_DIR / "recipes"
IMAGES_DIR = DATA_DIR / "images"
PROGRESS_FILE = DATA_DIR / "images_progress.json"
DELAY_BETWEEN_DOWNLOADS = 0.5  # lighter rate limit — images are on CDN
CONCURRENT_DOWNLOADS = 5
WEBP_QUALITY = 80


def load_progress() -> set[str]:
    if PROGRESS_FILE.exists():
        with open(PROGRESS_FILE) as f:
            return set(json.load(f).get("completed", []))
    return set()


def save_progress(completed: set[str]):
    with open(PROGRESS_FILE, "w") as f:
        json.dump({"completed": list(completed), "count": len(completed)}, f)


def has_cwebp() -> bool:
    try:
        subprocess.run(["cwebp", "-version"], capture_output=True, check=True)
        return True
    except (FileNotFoundError, subprocess.CalledProcessError):
        return False


def convert_to_webp(input_path: Path, output_path: Path) -> bool:
    try:
        subprocess.run(
            ["cwebp", "-q", str(WEBP_QUALITY), str(input_path), "-o", str(output_path)],
            capture_output=True, check=True,
        )
        input_path.unlink()
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        # Fallback: keep original
        if input_path.exists() and not output_path.exists():
            input_path.rename(output_path.with_suffix(input_path.suffix))
        return False


async def download_image(
    session: aiohttp.ClientSession,
    url: str,
    recipe_id: str,
    semaphore: asyncio.Semaphore,
    use_cwebp: bool,
) -> bool:
    async with semaphore:
        try:
            # Use higher-res image URL variant
            image_url = url.replace("t_web_shared_recipe_221x240", "t_web_rdp_recipe_584x480_1_5x")
            async with session.get(image_url, timeout=aiohttp.ClientTimeout(total=30)) as resp:
                if resp.status != 200:
                    return False
                content = await resp.read()
                content_type = resp.headers.get("Content-Type", "")
                ext = ".jpg"
                if "png" in content_type:
                    ext = ".png"
                elif "webp" in content_type:
                    ext = ".webp"

                if ext == ".webp" or not use_cwebp:
                    out_path = IMAGES_DIR / f"{recipe_id}{ext if not use_cwebp else '.webp'}"
                    out_path.write_bytes(content)
                else:
                    tmp_path = IMAGES_DIR / f"{recipe_id}{ext}"
                    tmp_path.write_bytes(content)
                    out_path = IMAGES_DIR / f"{recipe_id}.webp"
                    convert_to_webp(tmp_path, out_path)

                return True
        except Exception:
            return False


async def run_download():
    IMAGES_DIR.mkdir(parents=True, exist_ok=True)

    recipe_files = sorted(RECIPES_DIR.glob("*.json"))
    if not recipe_files:
        raise SystemExit("No recipe files found. Run 02_extract.py first.")

    use_cwebp = has_cwebp()
    if not use_cwebp:
        print("WARNING: cwebp not found. Images will be saved in original format.")
        print("Install with: sudo pacman -S libwebp  (or apt install webp)")

    # Build download list
    completed = load_progress()
    tasks = []
    for rf in recipe_files:
        with open(rf) as f:
            recipe = json.load(f)
        rid = recipe.get("id", rf.stem)
        if rid in completed:
            continue
        image_url = recipe.get("image") or recipe.get("thumbnail") or recipe.get("_meta", {}).get("image")
        if not image_url:
            completed.add(rid)
            continue
        tasks.append((rid, image_url))

    print(f"Total recipes: {len(recipe_files)}, already done: {len(completed)}, to download: {len(tasks)}")

    if not tasks:
        print("All images already downloaded.")
        return

    semaphore = asyncio.Semaphore(CONCURRENT_DOWNLOADS)
    errors = []

    async with aiohttp.ClientSession() as session:
        for i, (rid, url) in enumerate(tasks):
            ok = await download_image(session, url, rid, semaphore, use_cwebp)
            if ok:
                completed.add(rid)
            else:
                errors.append(rid)

            if (i + 1) % 50 == 0:
                save_progress(completed)
                pct = (len(completed) / len(recipe_files)) * 100
                print(f"  [{len(completed)}/{len(recipe_files)}] ({pct:.1f}%)")

            await asyncio.sleep(DELAY_BETWEEN_DOWNLOADS)

    save_progress(completed)
    print(f"\nDownload complete: {len(completed)}/{len(recipe_files)}")
    if errors:
        print(f"{len(errors)} failed: {errors[:10]}...")


if __name__ == "__main__":
    asyncio.run(run_download())

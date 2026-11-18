"""Phase 7: construit la base SQLite pré-remplie des recettes françaises.

Entrées : data/recipes (02), data/ratings (04), data/ratings_bulk.json, data/details (06, optionnel).
Sortie  : android/app/src/main/assets/coquilledure_seed.db
  - recipes            : mêmes colonnes que RecipeEntity (hors isFavorite)
  - collections        : id, name, cover, total
  - collection_recipes : collectionId, recipeId
Au premier lancement, l'app l'ATTACH et copie les lignes dans Room (DataSeeder).
Périmètre : recettes dont data/ratings/{id}.json existe (= françaises).
"""

import collections
import json
import re
import sqlite3
from pathlib import Path

DATA = Path(__file__).parent / "data"
OUT = Path(__file__).parent.parent / "android/app/src/main/assets/coquilledure_seed.db"
ICON_URL = "https://assets.tmecosys.com/image/upload/icons/ingredient_icons/{}"

SCHEMA = """
CREATE TABLE recipes (
  id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, image TEXT NOT NULL, thumbnail TEXT NOT NULL,
  difficulty TEXT NOT NULL, activeTime INTEGER NOT NULL, totalTime INTEGER NOT NULL, servingSize INTEGER NOT NULL,
  categoriesJson TEXT NOT NULL, ingredientsJson TEXT NOT NULL, stepGroupsJson TEXT NOT NULL,
  notesJson TEXT NOT NULL, nutritionJson TEXT NOT NULL, rating REAL, ratingCount INTEGER NOT NULL,
  tmVersions TEXT NOT NULL, utensilsJson TEXT NOT NULL, collectionsJson TEXT NOT NULL,
  categoryIds TEXT NOT NULL, tagsJson TEXT NOT NULL, country TEXT NOT NULL, devicesJson TEXT NOT NULL,
  variantsJson TEXT NOT NULL, relatedIds TEXT NOT NULL, carbs REAL
);
CREATE TABLE collections (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, cover TEXT NOT NULL, total INTEGER NOT NULL, description TEXT NOT NULL DEFAULT '');
CREATE TABLE collection_recipes (collectionId TEXT NOT NULL, recipeId TEXT NOT NULL, PRIMARY KEY (collectionId, recipeId));
"""


def load(p: Path, default=None):
    try:
        return json.loads(p.read_text())
    except (OSError, ValueError):
        return default


def clean(text: str) -> str:
    return re.sub(r"</?nobr>", "", text or "")


def nutrition(groups):
    entries = []
    for g in groups or []:
        entries.extend(g.get("recipe_nutritions") or [])
    pick = next((e for e in entries if "portion" in (e.get("unit_notation") or "") and e.get("quantity") == 1), None)
    pick = pick or (entries[0] if entries else None)
    return [
        {"type": n["type"], "number": n["number"], "unit": n.get("unittype", "")}
        for n in (pick or {}).get("nutritions", [])
    ]


def main():
    ids = sorted(p.stem for p in (DATA / "ratings").glob("*.json"))
    raws = {i: load(DATA / "recipes" / f"{i}.json") for i in ids}
    raws = {i: r for i, r in raws.items() if r}
    # nom canonique d'une catégorie = nom le plus fréquent de son id
    names = collections.defaultdict(collections.Counter)
    for r in raws.values():
        for c in r["categories"]:
            names[c["id"]][c["name"]] += 1
    canon = {k: v.most_common(1)[0][0] for k, v in names.items()}

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.unlink(missing_ok=True)
    db = sqlite3.connect(OUT)
    db.executescript(SCHEMA)
    col_names, col_total, col_cover, col_rec = {}, {}, {}, collections.defaultdict(list)
    col_desc = {}
    for p in (DATA / "collections").glob("*.json"):
        d = load(p, {})
        if d.get("description"):
            col_desc[d["id"]] = d["description"]
    rows = []
    for rid, r in raws.items():
        rt = load(DATA / "ratings" / f"{rid}.json", {})
        det = load(DATA / "details" / f"{rid}.json", {})
        cat_of = {i["id"]: i for i in rt.get("ingredients", [])}
        ingredients = []
        for ing in r["ingredients"]:
            n = ing["id"].rsplit("-", 1)[-1]
            meta = cat_of.get(n, {})
            cat = meta.get("cat")
            ingredients.append({
                "id": ing["id"], "name": ing["name"], "description": ing["description"],
                "preparation": ing.get("preparation"),
                "shopping_category": int(cat) if cat and cat.isdigit() else None,
                "icon": ICON_URL.format(n) if meta.get("icon") else None,
            })
        categories = [{"id": c["id"], "name": canon[c["id"]]} for c in r["categories"]]
        steps = [
            {"title": g["title"], "steps": [{"title": s["title"], "text": clean(s["formatted_text"])} for s in g["recipe_steps"]]}
            for g in r["step_groups"]
        ]
        nut = nutrition(r["nutrition_groups"])
        carbs = next((n["number"] for n in nut if n["type"] == "carb2"), None)
        cols = [{"id": c["id"], "name": c["name"], "total": c["total_recipes"]} for c in r["collections"] if c.get("name")]
        for c in cols:
            col_names[c["id"]] = c["name"]
            col_total[c["id"]] = c["total"]
            col_rec[c["id"]].append(rid)
        for c in det.get("collections", []):
            if c.get("cover"):
                col_cover[c["id"]] = c["cover"].replace("{transformation}", "t_web_shared_recipe_221x240")
        rows.append((
            rid, r["name"], r["image"], r["thumbnail"], r["difficulty"], r["active_time"], r["total_time"],
            r["serving_size"], json.dumps(categories, ensure_ascii=False),
            json.dumps(ingredients, ensure_ascii=False), json.dumps(steps, ensure_ascii=False),
            json.dumps(r["notes"], ensure_ascii=False), json.dumps(nut), rt.get("rating"), rt.get("rating_count", 0),
            ",".join(rt.get("tm_versions", [])), json.dumps(r["utensils"], ensure_ascii=False),
            json.dumps(cols, ensure_ascii=False), ",".join(c["id"] for c in categories),
            json.dumps([t["name"] for t in det.get("tags", [])], ensure_ascii=False),
            ",".join(det.get("countries", [])), json.dumps(det.get("devices", []), ensure_ascii=False),
            json.dumps(load(DATA / "variants" / f"{rid}.json", []), ensure_ascii=False), "", carbs,
        ))
    db.executemany("INSERT INTO recipes VALUES (" + ",".join("?" * 25) + ")", rows)
    db.executemany(
        "INSERT INTO collections VALUES (?,?,?,?,?)",
        # total = recettes réellement présentes dans la base (pas le total de la source)
        [(i, n, col_cover.get(i, ""), len(set(col_rec[i])), col_desc.get(i, "")) for i, n in col_names.items()],
    )
    db.executemany(
        "INSERT OR IGNORE INTO collection_recipes VALUES (?,?)",
        [(c, r) for c, rs in col_rec.items() for r in rs],
    )
    db.commit()
    db.execute("VACUUM")
    db.close()
    print(f"{len(rows)} recettes, {len(col_names)} collections, {OUT.stat().st_size / 1e6:.1f} Mo")


if __name__ == "__main__":
    main()

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Projet

« Coquille dure » : clone Android **hors ligne** de l'app Cookidoo (Thermomix). Objectif : UI/UX identiques à l'original. Texte et commentaires du projet en français. Trois parties :

- `android/` : app Kotlin (Jetpack Compose, Material3, Room, Hilt, Coil, Gson). Package/applicationId `fr.coquilledure` (ancien `fr.cookidur` obsolète). minSdk 26, portrait seul.
- `scraper/` : pipeline Python numéroté (`01_…` à `09_…`) qui scrape Cookidoo via `cookidoo-api` et produit la base seed. Données brutes dans `scraper/data/` (ignoré par git). Notes API dans `scraper/REVERSE_NOTES.md`.
- `qa/` : scripts de QA multi-appareils via adb. `tools/gen_egg.py` génère logo et splash.

`docs/cookidoo_ref/` (captures de référence de l'app officielle) et `.env` (identifiants Cookidoo, jamais en dur dans le code) sont ignorés par git.

## Commandes

Build (pas de java dans le PATH) :

```
cd android && JAVA_HOME=~/Android/jdk ./gradlew assembleDebug
```

APK : `android/app/build/outputs/apk/debug/app-debug.apk`. Env : `ANDROID_HOME=~/Android/sdk`, adb dans `~/Android/sdk/platform-tools/adb`. Installer sur tous les appareils connectés : `bash qa/install_all.sh`. Il n'y a pas de tests unitaires ni de lint configurés.

QA :
- `python3 qa/shoot.py SERIAL TAG [--font F] [--night] [--dens D] [--ime]` : parcours de l'app + captures dans `qa/shots/TAG`.
- `qa/matrix_lite.sh SERIAL PROFIL` (base, police 1.3, sombre) ; `qa/matrix.sh SERIAL PROFIL DENSITE` (5 variantes) ; `qa/sheet.py` fait une planche de captures (nécessite pillow).
- AVD : `qa_small_api26`, `qa_mid_api30`, `qa_big_api34`, `qa_pixel_api36`, `qa_fold_api34`. Max 2 émulateurs à la fois, avec `-no-window`, arrêt par `adb emu kill`. Ne jamais `pkill -f`.

Scraper : venv `.venv`, `pip install -r scraper/requirements.txt`, scripts lancés dans l'ordre numérique depuis `scraper/`. Identifiants via `.env`/variables d'environnement.

## Architecture

**Données : seed pré-remplie.** `scraper/07_build_seed.py` construit `android/app/src/main/assets/coquilledure_seed.db` (~60 Mo, versionnée dans git) : tables `recipes` (mêmes colonnes que `RecipeEntity`, sans `isFavorite`), `collections`, `collection_recipes`. Seules les recettes françaises (celles qui ont `data/ratings/{id}.json`) sont incluses. Au premier lancement, `data/seed/DataSeeder.kt` copie les lignes de la seed dans Room, ligne à ligne (pas d'`ATTACH`, qui désactive le WAL et plante sur Android 16), en préservant les favoris. Il rejoue l'import quand `SEED_VERSION` augmente.

**Piège de cohérence :** le schéma de la seed, `RecipeEntity`/`CoquilleDureDatabase` (Room, version 10) et les colonnes copiées par `DataSeeder` doivent rester alignés. Si la seed change, vérifier que l'APK la contient et que `DataSeeder` colle au schéma. Ajouter une colonne implique de toucher le scraper, l'entité, la migration Room et `DataSeeder`.

**Couches de l'app** (`android/app/src/main/java/fr/coquilledure/`) : `data/db` (entités, DAO Room, `RecipeFilter` pour la recherche, FTS), `data/repository` (Recipe, Planner, UserContent), `data/shopping/ShoppingLogic.kt`, `di/AppModule.kt` (Hilt), `ui/<écran>` (home, search/Découvrir, planner, favorites/Mes recettes, recipe, cooking, profile) avec ViewModels Hilt, `ui/components` (cartes recette, feuilles d'actions ⋮, sélecteur de date), `ui/navigation` (barre du bas à 5 onglets). Écran principal dans `MainActivity.kt`.

**Contraintes UI :** police plafonnée à 1.3 (`Theme.kt`) ; l'app est distribuée à des proches (Android 8+) donc tester petit écran, police agrandie, mode sombre. Fonctions en ligne de Cookidoo (abonnement, traduire, notifications push) sont volontairement hors périmètre. L'Assistant est refait **hors ligne, sans IA ni compte** (`data/assistant/`, écran `ui/assistant/`) : `AssistantIntentParser` (règles FR → ingrédients, type de plat, durée, personnes, régime, « sans X ») → recherche FTS via `RecipeFilter` + vérification exacte des ingrédients → phrases par gabarits (`AssistantPhrasing`). Tests JVM : `cd android && JAVA_HOME=~/Android/jdk ./gradlew testDebugUnitTest`.

## Planification

`PLAN.md` et `PLAN_MANQUES.md` (écarts avec Cookidoo, référencent des captures `[h2]`, `[s4]`…) sont supprimés du working tree mais restent dans l'historique git (`git show HEAD:PLAN.md`).

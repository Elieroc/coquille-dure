package fr.coquilledure.data.cookidoo

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import fr.coquilledure.data.db.*
import fr.coquilledure.data.db.entities.*
import fr.coquilledure.data.shopping.ShoppingLogic
import javax.inject.Inject
import javax.inject.Singleton
import java.time.LocalDate

/**
 * Écrit un [CookidooSnapshot] dans Room, en une seule transaction (tout ou rien).
 * Réimporter ne crée pas de doublons : favoris/notes/collections en upsert, listes appariées par nom,
 * Ma semaine et courses complétées seulement de ce qui manque, créations à identifiant stable.
 * Seules les recettes présentes dans la base de l'app sont reliées ; les autres vont dans `missing`.
 */
@Singleton
class CookidooImporter @Inject constructor(
    private val db: CoquilleDureDatabase,
    private val context: Context,
) {
    private val gson = Gson()

    suspend fun import(
        snap: CookidooSnapshot,
        selected: Set<ImportCategory>,
        mode: ImportMode,
        ratings: Map<String, Int>,
    ): ImportReport {
        val recipes = db.recipeDao(); val planner = db.plannerDao(); val shopping = db.shoppingDao()
        val collections = db.collectionDao(); val user = db.recipeUserDao(); val lists = db.recipeListDao()
        val replace = mode == ImportMode.REPLACE
        val imported = linkedMapOf<ImportCategory, Int>()
        val missing = linkedMapOf<String, String>()
        val names = (snap.favorites.map { it.id to it.name } + snap.shopRecipes.map { it.recipeId to it.title }).toMap()

        db.withTransaction {
            // Créations d'abord : listes et Ma semaine peuvent y renvoyer.
            val creationIds = snap.creations.associate { it.id to "u" + stableId(it.id) }
            if (ImportCategory.CREATIONS in selected) {
                if (replace) recipes.deleteCreations()
                recipes.insertRecipes(snap.creations.map(::creationEntity))
                imported[ImportCategory.CREATIONS] = snap.creations.size
            }

            /** Identifiant local d'une recette du compte, ou null si elle n'existe pas dans l'app. */
            val known = HashSet<String>()
            suspend fun resolve(ids: Collection<String>): Map<String, String?> {
                val wanted = ids.filter { it !in creationIds }.distinct()
                wanted.chunked(500).forEach { known += recipes.existingIds(it) }
                return ids.distinct().associateWith { id ->
                    when {
                        id in creationIds -> creationIds[id].takeIf { ImportCategory.CREATIONS in selected || recipes.getRecipeById(it!!) != null }
                        id in known -> id
                        else -> null
                    }
                }
            }
            fun miss(id: String) { missing.putIfAbsent(id, names[id].orEmpty()) }

            if (ImportCategory.FAVORITES in selected) {
                if (replace) recipes.clearFavorites()
                val map = resolve(snap.favorites.map { it.id })
                snap.favorites.forEach { f -> map[f.id]?.let { recipes.setFavorite(it, true) } ?: miss(f.id) }
                imported[ImportCategory.FAVORITES] = map.values.count { it != null }
            }

            if (ImportCategory.LISTS in selected) {
                if (replace) { lists.clearAllItems(); lists.clearAllLists() }
                val map = resolve(snap.lists.flatMap { it.recipeIds })
                snap.lists.forEach { l ->
                    val id = lists.idByName(l.name) ?: lists.create(RecipeListEntity(name = l.name, createdAt = System.currentTimeMillis()))
                    l.recipeIds.forEach { r -> map[r]?.let { lists.add(RecipeListItemEntity(id, it)) } ?: miss(r) }
                }
                imported[ImportCategory.LISTS] = snap.lists.size
            }

            if (ImportCategory.COLLECTIONS in selected) {
                if (replace) collections.clearSaved()
                var n = 0
                snap.collections.forEach { c ->
                    if (collections.getById(c.id) != null) { collections.save(SavedCollectionEntity(c.id, System.currentTimeMillis())); n++ }
                    else missing.putIfAbsent(c.id, c.name)
                }
                imported[ImportCategory.COLLECTIONS] = n
            }

            if (ImportCategory.WEEK in selected) {
                if (replace) planner.clearPlanned()
                val today = LocalDate.now().toString()
                val map = resolve(snap.week.flatMap { it.recipeIds + it.creationIds })
                var n = 0
                snap.week.filter { it.date >= today }.forEach { d ->
                    // On ajoute seulement ce qui manque pour un même (jour, recette).
                    (d.recipeIds + d.creationIds).groupingBy { it }.eachCount().forEach { (rid, want) ->
                        val local = map[rid] ?: return@forEach miss(rid)
                        repeat(want - planner.countPlanned(local, d.date)) {
                            planner.insertPlanned(PlannedRecipeEntity(recipeId = local, date = d.date)); n++
                        }
                    }
                }
                imported[ImportCategory.WEEK] = n
            }

            if (ImportCategory.SHOPPING in selected) {
                if (replace) shopping.clearAll()
                val map = resolve(snap.shopRecipes.map { it.recipeId })
                var n = 0
                snap.shopRecipes.groupBy { it.recipeId }.forEach { (rid, copies) ->
                    val local = map[rid] ?: return@forEach miss(rid)
                    val have = shopping.getRecipe(local)?.count ?: 0
                    copies.drop(have).forEach { copy ->
                        shopping.addRecipeCopy(local, copy.items.map { it.toEntity(local) }); n++
                    }
                }
                val existing = shopping.freeItemNames().map(ShoppingLogic::normalize).toHashSet()
                val free = snap.shopFree.filter { ShoppingLogic.normalize(it.name) !in existing }
                shopping.insertItems(free.map { it.toEntity(null) }); n += free.size
                imported[ImportCategory.SHOPPING] = n
            }

            if (ImportCategory.RATINGS in selected) {
                if (replace) user.clearRatings()
                val map = resolve(ratings.keys)
                ratings.forEach { (id, r) -> map[id]?.let { user.saveRating(RecipeRatingEntity(it, r)) } }
                imported[ImportCategory.RATINGS] = ratings.keys.count { map[it] != null }
            }
        }

        if (ImportCategory.PROFILE in selected && snap.profileName.isNotBlank()) {
            val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
            if (replace || prefs.getString("name", "").isNullOrBlank()) prefs.edit().putString("name", snap.profileName).apply()
            imported[ImportCategory.PROFILE] = 1
        }
        return ImportReport(imported, missing.map { it.key to it.value }, snap.unavailable.filterKeys { it in selected })
    }

    private fun CkShopItem.toEntity(recipeId: String?) = ShoppingItemEntity(
        ingredientId = ingredientRef, name = name, quantity = quantity, categoryId = categoryId,
        checked = owned, recipeId = recipeId, ownedAt = if (owned) System.currentTimeMillis() else 0,
    )

    /** Même format que l'éditeur de recettes (ingrédients `{id,name,description,…}`, étapes `{title,text}`). */
    private fun creationEntity(c: CkCreation): RecipeEntity {
        val steps = c.steps.mapIndexed { i, t -> mapOf("title" to "${i + 1}", "text" to t) }
        return RecipeEntity(
            id = "u" + stableId(c.id), name = c.name, image = c.image.orEmpty(), thumbnail = c.image.orEmpty(),
            difficulty = "", activeTime = c.activeSec, totalTime = c.totalSec, servingSize = c.servings,
            categoriesJson = "[]",
            ingredientsJson = gson.toJson(c.ingredients.mapIndexed { i, t ->
                mapOf("id" to "user-$i", "name" to t, "description" to "", "preparation" to null, "shopping_category" to null, "icon" to null)
            }),
            stepGroupsJson = if (steps.isEmpty()) "[]" else gson.toJson(listOf(mapOf("title" to "", "steps" to steps))),
            notesJson = gson.toJson(c.hints), nutritionJson = "[]",
        )
    }
}

package fr.coquilledure.data.repository

import fr.coquilledure.data.db.CollectionDao
import fr.coquilledure.data.db.RecipeDao
import fr.coquilledure.data.db.RecipeListDao
import fr.coquilledure.data.db.entities.RecipeEntity
import fr.coquilledure.data.db.entities.RecipeListEntity
import fr.coquilledure.data.db.entities.RecipeListItemEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Listes personnalisées et recettes créées par l'utilisateur (id préfixé `u`, stockées dans `recipes`). */
@Singleton
class UserContentRepository @Inject constructor(
    private val recipes: RecipeDao,
    private val lists: RecipeListDao,
    private val collections: CollectionDao,
) {
    val creations = recipes.getCreations()
    val allLists = lists.lists()
    val savedCollections = collections.saved()

    suspend fun createList(name: String): Long = lists.create(RecipeListEntity(name = name.trim(), createdAt = System.currentTimeMillis()))
    suspend fun deleteList(id: Long) { lists.clear(id); lists.delete(id) }
    suspend fun addToList(listId: Long, recipeId: String) = lists.add(RecipeListItemEntity(listId, recipeId))
    suspend fun removeFromList(listId: Long, recipeId: String) = lists.remove(listId, recipeId)
    suspend fun listName(id: Long) = lists.name(id)
    fun listRecipes(id: Long) = lists.recipes(id)

    private fun newId() = "u" + System.currentTimeMillis()

    suspend fun createRecipe(name: String): String {
        val id = newId()
        recipes.insertRecipes(listOf(blank(id, name.trim())))
        return id
    }

    suspend fun duplicate(r: RecipeEntity): String {
        val id = newId()
        recipes.insertRecipes(listOf(r.copy(id = id, isFavorite = false, rating = null, ratingCount = 0, tmVersions = "", categoryIds = "", collectionsJson = "[]")))
        return id
    }

    suspend fun save(r: RecipeEntity) = recipes.insertRecipes(listOf(r))
    suspend fun delete(id: String) = recipes.deleteRecipe(id)
    suspend fun get(id: String) = recipes.getRecipeById(id)

    private fun blank(id: String, name: String) = RecipeEntity(
        id = id, name = name, image = "", thumbnail = "", difficulty = "", activeTime = 0, totalTime = 0, servingSize = 4,
        categoriesJson = "[]", ingredientsJson = "[]", stepGroupsJson = "[]", notesJson = "[]", nutritionJson = "[]",
    )
}

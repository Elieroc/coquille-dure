package fr.coquilledure.data.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import fr.coquilledure.data.db.PlannerDao
import fr.coquilledure.data.db.ShoppingDao
import fr.coquilledure.data.db.entities.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlannerRepository @Inject constructor(private val dao: PlannerDao) {
    val planned: Flow<List<PlannedWithRecipe>> = dao.observePlanned()

    suspend fun add(recipeId: String, date: LocalDate) =
        dao.insertPlanned(PlannedRecipeEntity(recipeId = recipeId, date = date.toString()))

    suspend fun remove(id: Long) = dao.deletePlanned(id)
    suspend fun move(id: Long, date: LocalDate) = dao.movePlanned(id, date.toString())
}

@Singleton
class ShoppingRepository @Inject constructor(private val dao: ShoppingDao) {
    val items: Flow<List<ShoppingItemEntity>> = dao.observeItems()
    val recipes: Flow<List<ShoppingRecipeWithRecipe>> = dao.observeRecipes()

    suspend fun addRecipe(recipe: RecipeEntity) {
        val array = Gson().fromJson(recipe.ingredientsJson, JsonArray::class.java)
        val items = array.map { it.asJsonObject }.map { o ->
            fun str(k: String) = o.get(k)?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            ShoppingItemEntity(
                ingredientId = str("id").ifBlank { null },
                name = str("name"),
                quantity = str("description"),
                categoryId = o.get("shopping_category")?.takeIf { !it.isJsonNull }?.asInt,
                recipeId = recipe.id,
            )
        }
        dao.addRecipeCopy(recipe.id, items)
    }

    suspend fun addCustom(name: String) = dao.insertItems(listOf(ShoppingItemEntity(name = name.trim(), quantity = "")))

    suspend fun setChecked(ids: List<Long>, checked: Boolean) =
        dao.setChecked(ids, checked, if (checked) System.currentTimeMillis() else 0)

    suspend fun deleteItems(ids: List<Long>) = dao.deleteItems(ids)
    suspend fun removeRecipe(recipeId: String) = dao.removeRecipe(recipeId)
    suspend fun clearAll() = dao.clearAll()
}

package fr.coquilledure.data.db

import androidx.room.*
import fr.coquilledure.data.db.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannerDao {
    @Transaction
    @Query("SELECT * FROM planned_recipes ORDER BY date, id")
    fun observePlanned(): Flow<List<PlannedWithRecipe>>

    @Insert
    suspend fun insertPlanned(entry: PlannedRecipeEntity)

    @Query("DELETE FROM planned_recipes WHERE id = :id")
    suspend fun deletePlanned(id: Long)

    @Query("UPDATE planned_recipes SET date = :date WHERE id = :id")
    suspend fun movePlanned(id: Long, date: String)

    @Query("SELECT COUNT(*) FROM planned_recipes WHERE recipeId = :recipeId AND date = :date")
    suspend fun countPlanned(recipeId: String, date: String): Int

    @Query("DELETE FROM planned_recipes")
    suspend fun clearPlanned()
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY id")
    fun observeItems(): Flow<List<ShoppingItemEntity>>

    @Transaction
    @Query("SELECT * FROM shopping_recipes ORDER BY addedAt")
    fun observeRecipes(): Flow<List<ShoppingRecipeWithRecipe>>

    @Query("SELECT * FROM shopping_recipes WHERE recipeId = :recipeId")
    suspend fun getRecipe(recipeId: String): ShoppingRecipeEntity?

    @Insert
    suspend fun insertItems(items: List<ShoppingItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecipe(entry: ShoppingRecipeEntity)

    @Query("DELETE FROM shopping_items WHERE recipeId = :recipeId")
    suspend fun deleteItemsOfRecipe(recipeId: String)

    @Query("DELETE FROM shopping_recipes WHERE recipeId = :recipeId")
    suspend fun deleteRecipe(recipeId: String)

    @Query("UPDATE shopping_items SET checked = :checked, ownedAt = :at WHERE id IN (:ids)")
    suspend fun setChecked(ids: List<Long>, checked: Boolean, at: Long)

    @Query("DELETE FROM shopping_items WHERE id IN (:ids)")
    suspend fun deleteItems(ids: List<Long>)

    @Query("SELECT name FROM shopping_items WHERE recipeId IS NULL")
    suspend fun freeItemNames(): List<String>

    @Query("DELETE FROM shopping_items")
    suspend fun clearItems()

    @Query("DELETE FROM shopping_recipes")
    suspend fun clearRecipes()

    @Transaction
    suspend fun addRecipeCopy(recipeId: String, items: List<ShoppingItemEntity>) {
        val existing = getRecipe(recipeId)
        upsertRecipe(ShoppingRecipeEntity(recipeId, (existing?.count ?: 0) + 1, existing?.addedAt ?: System.currentTimeMillis()))
        insertItems(items)
    }

    @Transaction
    suspend fun removeRecipe(recipeId: String) {
        deleteItemsOfRecipe(recipeId)
        deleteRecipe(recipeId)
    }

    @Transaction
    suspend fun clearAll() {
        clearItems()
        clearRecipes()
    }
}

package fr.coquilledure.data.db

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import fr.coquilledure.data.db.entities.RecipeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY name LIMIT :limit OFFSET :offset")
    fun getRecipes(limit: Int = 50, offset: Int = 0): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getRecipeById(id: String): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE isFavorite = 1 ORDER BY name")
    fun getFavorites(): Flow<List<RecipeEntity>>

    @Query("""
        SELECT recipes.* FROM recipes
        JOIN recipes_fts ON recipes.rowid = recipes_fts.rowid
        WHERE recipes_fts MATCH :query
        ORDER BY recipes.ratingCount DESC
    """)
    fun searchRecipes(query: String): Flow<List<RecipeEntity>>

    @RawQuery(observedEntities = [RecipeEntity::class])
    fun filter(query: SupportSQLiteQuery): Flow<List<RecipeEntity>>

    @RawQuery(observedEntities = [RecipeEntity::class])
    fun filterCount(query: SupportSQLiteQuery): Flow<Int>

    @Query("SELECT * FROM recipes WHERE id LIKE 'u%' ORDER BY CAST(SUBSTR(id, 2) AS INTEGER) DESC")
    fun getCreations(): Flow<List<RecipeEntity>>

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteRecipe(id: String)

    @Query("SELECT * FROM recipes WHERE totalTime <= 1800 AND totalTime > 0 AND rating >= 4.3 AND ratingCount >= 30 AND id LIKE 'r%' ORDER BY ratingCount DESC LIMIT :limit")
    suspend fun quick(limit: Int): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE carbs IS NOT NULL AND carbs <= 15 AND rating >= 4.3 AND ratingCount >= 20 AND categoryIds NOT LIKE '%RPF-011%' AND categoryIds NOT LIKE '%RPF-013%' AND id LIKE 'r%' ORDER BY ratingCount DESC LIMIT :limit")
    suspend fun lowCarb(limit: Int): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE categoryIds LIKE '%RPF-006%' AND rating >= 4.3 AND ratingCount >= 20 ORDER BY ratingCount DESC LIMIT :limit")
    suspend fun vegetarian(limit: Int): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE (tagsJson LIKE '%végétal%' OR tagsJson LIKE '%vegan%' OR name LIKE '%végétal%' OR name LIKE '%vegan%') AND rating >= 4.0 ORDER BY ratingCount DESC LIMIT :limit")
    suspend fun vegan(limit: Int): List<RecipeEntity>

    @RawQuery
    suspend fun countOnce(query: SupportSQLiteQuery): Int

    @Query("SELECT name FROM recipes WHERE name LIKE :pattern ORDER BY ratingCount DESC LIMIT :limit")
    suspend fun namesLike(pattern: String, limit: Int): List<String>

    @Query("SELECT * FROM recipes WHERE rating >= 4.3 AND ratingCount >= 30 ORDER BY ratingCount DESC LIMIT :limit")
    fun getPopular(limit: Int): Flow<List<RecipeEntity>>

    @Query("SELECT DISTINCT json_each.value FROM recipes, json_each(recipes.categoriesJson) ORDER BY value")
    fun getCategories(): Flow<List<String>>

    @Query("SELECT * FROM recipes WHERE categoriesJson LIKE '%' || :categoryName || '%' ORDER BY name")
    fun getRecipesByCategory(categoryName: String): Flow<List<RecipeEntity>>

    @Query("""
        SELECT * FROM recipes WHERE id != :id AND categoryIds LIKE '%' || :categoryId || '%' AND ratingCount >= 5
        ORDER BY rating DESC, ratingCount DESC LIMIT :limit
    """)
    suspend fun getSimilar(id: String, categoryId: String, limit: Int): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE id IN (SELECT recipeId FROM collection_recipes WHERE collectionId = :collectionId) LIMIT :limit")
    suspend fun inCollection(collectionId: String, limit: Int): List<RecipeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipes(recipes: List<RecipeEntity>)

    @Query("UPDATE recipes SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun count(): Int

    @Query("UPDATE recipes SET rating = :rating, ratingCount = :count, tmVersions = :tm, ingredientsJson = :ingredients WHERE id = :id")
    suspend fun updateMeta(id: String, rating: Double?, count: Int, tm: String, ingredients: String)

    @Query("SELECT COUNT(*) FROM recipes WHERE tmVersions != ''")
    suspend fun countWithMeta(): Int

    // --- import Cookidoo ---
    @Query("SELECT id FROM recipes WHERE id IN (:ids)")
    suspend fun existingIds(ids: List<String>): List<String>

    @Query("UPDATE recipes SET isFavorite = 0")
    suspend fun clearFavorites()

    @Query("DELETE FROM recipes WHERE id LIKE 'u%'")
    suspend fun deleteCreations()
}

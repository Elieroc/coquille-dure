package fr.coquilledure.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import fr.coquilledure.data.db.entities.CollectionEntity
import fr.coquilledure.data.db.entities.RecipeEntity
import fr.coquilledure.data.db.entities.SavedCollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections WHERE total >= 3 ORDER BY name")
    fun getAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getById(id: String): CollectionEntity?

    @Query("""
        SELECT recipes.* FROM recipes
        JOIN collection_recipes ON collection_recipes.recipeId = recipes.id
        WHERE collection_recipes.collectionId = :id ORDER BY recipes.name
    """)
    fun getRecipes(id: String): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM collections WHERE total >= 3 AND name LIKE '%' || :q || '%' ORDER BY total DESC")
    fun search(q: String): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE total >= 3 ORDER BY total DESC LIMIT :limit")
    suspend fun top(limit: Int): List<CollectionEntity>

    @Query("SELECT collections.* FROM collections JOIN saved_collections ON saved_collections.collectionId = collections.id ORDER BY savedAt DESC")
    fun saved(): Flow<List<CollectionEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_collections WHERE collectionId = :id)")
    fun isSaved(id: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(s: SavedCollectionEntity)

    @Query("DELETE FROM saved_collections WHERE collectionId = :id")
    suspend fun unsave(id: String)

    @Query("DELETE FROM saved_collections")
    suspend fun clearSaved()
}

package fr.coquilledure.data.db

import androidx.room.*
import fr.coquilledure.data.db.entities.RecipeEntity
import fr.coquilledure.data.db.entities.RecipeListEntity
import fr.coquilledure.data.db.entities.RecipeListItemEntity
import kotlinx.coroutines.flow.Flow

data class ListWithCount(val id: Long, val name: String, val count: Int)

@Dao
interface RecipeListDao {
    @Query("SELECT l.id AS id, l.name AS name, (SELECT COUNT(*) FROM recipe_list_items i WHERE i.listId = l.id) AS count FROM recipe_lists l ORDER BY l.createdAt DESC")
    fun lists(): Flow<List<ListWithCount>>

    @Query("SELECT name FROM recipe_lists WHERE id = :id")
    suspend fun name(id: Long): String?

    @Insert
    suspend fun create(l: RecipeListEntity): Long

    @Query("DELETE FROM recipe_lists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM recipe_list_items WHERE listId = :id")
    suspend fun clear(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(i: RecipeListItemEntity)

    @Query("DELETE FROM recipe_list_items WHERE listId = :listId AND recipeId = :recipeId")
    suspend fun remove(listId: Long, recipeId: String)

    @Query("SELECT recipes.* FROM recipes JOIN recipe_list_items ON recipe_list_items.recipeId = recipes.id WHERE recipe_list_items.listId = :id ORDER BY recipes.name")
    fun recipes(id: Long): Flow<List<RecipeEntity>>
}

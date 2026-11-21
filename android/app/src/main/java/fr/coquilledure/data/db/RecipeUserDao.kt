package fr.coquilledure.data.db

import androidx.room.*
import fr.coquilledure.data.db.entities.RecipeNoteEntity
import fr.coquilledure.data.db.entities.RecipeRatingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeUserDao {
    @Query("SELECT text FROM recipe_notes WHERE recipeId = :id")
    fun note(id: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNote(n: RecipeNoteEntity)

    @Query("DELETE FROM recipe_notes WHERE recipeId = :id")
    suspend fun deleteNote(id: String)

    @Query("SELECT rating FROM recipe_ratings WHERE recipeId = :id")
    fun rating(id: String): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRating(r: RecipeRatingEntity)

    @Query("DELETE FROM recipe_ratings")
    suspend fun clearRatings()
}

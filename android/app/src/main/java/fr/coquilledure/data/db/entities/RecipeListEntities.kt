package fr.coquilledure.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "recipe_lists")
data class RecipeListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "recipe_list_items", primaryKeys = ["listId", "recipeId"], indices = [Index("recipeId")])
data class RecipeListItemEntity(val listId: Long, val recipeId: String)

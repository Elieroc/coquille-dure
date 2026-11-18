package fr.coquilledure.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val cover: String,
    val total: Int,
    @ColumnInfo(defaultValue = "") val description: String = "",
)

@Entity(
    tableName = "collection_recipes",
    primaryKeys = ["collectionId", "recipeId"],
    indices = [Index("recipeId")],
)
data class CollectionRecipeEntity(
    val collectionId: String,
    val recipeId: String,
)

@Entity(tableName = "saved_collections")
data class SavedCollectionEntity(@PrimaryKey val collectionId: String, val savedAt: Long)

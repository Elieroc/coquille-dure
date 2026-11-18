package fr.coquilledure.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val image: String,
    val thumbnail: String,
    val difficulty: String,
    val activeTime: Int,
    val totalTime: Int,
    val servingSize: Int,
    val categoriesJson: String,
    val ingredientsJson: String,
    val stepGroupsJson: String,
    val notesJson: String,
    val nutritionJson: String,
    val isFavorite: Boolean = false,
    val rating: Double? = null,
    val ratingCount: Int = 0,
    val tmVersions: String = "",
    val utensilsJson: String = "[]",
    val collectionsJson: String = "[]",
    val categoryIds: String = "",
    val tagsJson: String = "[]",
    val country: String = "",
    val devicesJson: String = "[]",
    val variantsJson: String = "[]",
    val relatedIds: String = "",
    val carbs: Double? = null,
)

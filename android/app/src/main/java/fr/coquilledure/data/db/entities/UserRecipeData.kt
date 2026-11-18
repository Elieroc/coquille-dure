package fr.coquilledure.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipe_notes")
data class RecipeNoteEntity(@PrimaryKey val recipeId: String, val text: String)

@Entity(tableName = "recipe_ratings")
data class RecipeRatingEntity(@PrimaryKey val recipeId: String, val rating: Int)

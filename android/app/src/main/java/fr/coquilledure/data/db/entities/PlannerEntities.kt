package fr.coquilledure.data.db.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "planned_recipes")
data class PlannedRecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: String,
    val date: String,
)

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ingredientId: String? = null,
    val name: String,
    val quantity: String,
    val categoryId: Int? = null,
    val checked: Boolean = false,
    val recipeId: String? = null,
    val ownedAt: Long = 0,
)

@Entity(tableName = "shopping_recipes")
data class ShoppingRecipeEntity(
    @PrimaryKey val recipeId: String,
    val count: Int,
    val addedAt: Long,
)

data class PlannedWithRecipe(
    @Embedded val planned: PlannedRecipeEntity,
    @Relation(parentColumn = "recipeId", entityColumn = "id") val recipe: RecipeEntity?,
)

data class ShoppingRecipeWithRecipe(
    @Embedded val entry: ShoppingRecipeEntity,
    @Relation(parentColumn = "recipeId", entityColumn = "id") val recipe: RecipeEntity?,
)

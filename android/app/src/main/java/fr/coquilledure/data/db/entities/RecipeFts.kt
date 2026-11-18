package fr.coquilledure.data.db.entities

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

@Fts4(contentEntity = RecipeEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "recipes_fts")
data class RecipeFts(
    val name: String,
    val ingredientsJson: String,
)

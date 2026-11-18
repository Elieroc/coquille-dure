package fr.coquilledure.ui.recipe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.coquilledure.data.db.entities.RecipeEntity
import fr.coquilledure.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import fr.coquilledure.data.db.entities.RecipeNoteEntity
import fr.coquilledure.data.db.entities.RecipeRatingEntity
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Ingredient(val id: String, val name: String, val description: String, val preparation: String?, val icon: String? = null)
data class Step(val title: String, val text: String)
data class StepGroup(val title: String, val steps: List<Step>)
data class Category(val id: String, val name: String)
data class Variant(val id: String, val servings: Int)
data class CollectionRef(val id: String, val name: String, val total: Int)
data class Nutrition(val type: String, val number: Double, val unit: String)

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: RecipeRepository,
    private val userDao: fr.coquilledure.data.db.RecipeUserDao,
    private val recipeDao: fr.coquilledure.data.db.RecipeDao,
) : ViewModel() {

    private val gson = Gson()
    private val recipeId: String = checkNotNull(savedStateHandle["recipeId"])

    private val _recipe = MutableStateFlow<RecipeEntity?>(null)
    val recipe: StateFlow<RecipeEntity?> = _recipe

    val servingMultiplier = MutableStateFlow(1f)

    val note: StateFlow<String?> = userDao.note(recipeId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val userRating: StateFlow<Int?> = userDao.rating(recipeId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    /** Variantes de portions (id, portions, présente en base). */
    val variants = MutableStateFlow<List<Pair<Variant, Boolean>>>(emptyList())
    val similar = MutableStateFlow<List<RecipeEntity>>(emptyList())

    init {
        viewModelScope.launch {
            val r = repo.getRecipeById(recipeId)
            _recipe.value = r
            if (r != null) {
                val list: List<Variant> = gson.fromJson(r.variantsJson, object : TypeToken<List<Variant>>() {}.type) ?: emptyList()
                if (list.size > 1) variants.value = list.sortedBy { it.servings }.map { it to (it.id == r.id || repo.getRecipeById(it.id) != null) }
            }
            val cat = r?.categoryIds?.split(",")?.firstOrNull { it.isNotBlank() }
            if (r != null && cat != null) similar.value = recipeDao.getSimilar(r.id, cat, 12)
        }
    }

    fun saveNote(text: String) = viewModelScope.launch {
        if (text.isBlank()) userDao.deleteNote(recipeId) else userDao.saveNote(RecipeNoteEntity(recipeId, text.trim()))
    }

    fun saveRating(stars: Int) = viewModelScope.launch { userDao.saveRating(RecipeRatingEntity(recipeId, stars)) }

    fun parseStrings(json: String): List<String> =
        gson.fromJson<List<Any>>(json, object : TypeToken<List<Any>>() {}.type)
            ?.map { if (it is Map<*, *>) (it["name"] ?: it["title"] ?: "").toString() else it.toString() }
            ?.filter { it.isNotBlank() } ?: emptyList()

    fun parseCollections(json: String): List<CollectionRef> =
        gson.fromJson(json, object : TypeToken<List<CollectionRef>>() {}.type) ?: emptyList()

    fun parseIngredients(json: String): List<Ingredient> =
        gson.fromJson(json, object : TypeToken<List<Ingredient>>() {}.type) ?: emptyList()

    fun parseStepGroups(json: String): List<StepGroup> =
        gson.fromJson(json, object : TypeToken<List<StepGroup>>() {}.type) ?: emptyList()

    fun parseCategories(json: String): List<Category> =
        gson.fromJson(json, object : TypeToken<List<Category>>() {}.type) ?: emptyList()

    fun parseNutrition(json: String): List<Nutrition> =
        gson.fromJson(json, object : TypeToken<List<Nutrition>>() {}.type) ?: emptyList()

    fun toggleFavorite() {
        val r = _recipe.value ?: return
        viewModelScope.launch {
            repo.toggleFavorite(r.id, r.isFavorite)
            _recipe.value = r.copy(isFavorite = !r.isFavorite)
        }
    }

    fun setServings(multiplier: Float) { servingMultiplier.value = multiplier }
}

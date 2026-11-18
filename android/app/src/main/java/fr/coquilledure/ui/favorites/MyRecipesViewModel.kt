package fr.coquilledure.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.coquilledure.data.db.entities.RecipeEntity
import fr.coquilledure.data.repository.RecipeRepository
import fr.coquilledure.data.repository.UserContentRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.Normalizer
import javax.inject.Inject

enum class MySort(val label: String) { NAME("A - Z"), CREATED("Date de création"), SAVED("Sauvegarder") }

private fun String.fold() = Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

@HiltViewModel
class MyRecipesViewModel @Inject constructor(
    private val repo: RecipeRepository,
    private val user: UserContentRepository,
) : ViewModel() {
    val query = MutableStateFlow("")
    val sort = MutableStateFlow(MySort.NAME)

    val favorites = repo.getFavorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val creations = user.creations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val lists = user.allLists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val savedCollections = user.savedCollections.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filtered: StateFlow<List<RecipeEntity>> = combine(favorites, creations, query) { f, c, q ->
        if (q.isBlank()) emptyList() else (f + c).distinctBy { it.id }.filter { it.name.fold().contains(q.trim().fold()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun sorted(list: List<RecipeEntity>, s: MySort): List<RecipeEntity> = when (s) {
        MySort.NAME -> list.sortedBy { it.name.fold() }
        MySort.CREATED -> list.sortedByDescending { it.id.drop(1).toLongOrNull() ?: 0L }
        MySort.SAVED -> list
    }

    fun toggleFavorite(id: String, current: Boolean) { viewModelScope.launch { repo.toggleFavorite(id, current) } }
    fun createList(name: String, onDone: (Long) -> Unit = {}) { viewModelScope.launch { onDone(user.createList(name)) } }
    fun deleteList(id: Long) { viewModelScope.launch { user.deleteList(id) } }
    fun createRecipe(name: String, onDone: (String) -> Unit) { viewModelScope.launch { onDone(user.createRecipe(name)) } }
    fun deleteRecipe(id: String) { viewModelScope.launch { user.delete(id) } }
    fun listRecipes(id: Long) = user.listRecipes(id)
    fun removeFromList(listId: Long, recipeId: String) { viewModelScope.launch { user.removeFromList(listId, recipeId) } }
    suspend fun listName(id: Long) = user.listName(id)
    suspend fun recipe(id: String) = user.get(id)
    fun save(r: RecipeEntity) { viewModelScope.launch { user.save(r) } }
}

package fr.coquilledure.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.coquilledure.data.db.entities.RecipeEntity
import fr.coquilledure.data.repository.ShoppingRepository
import fr.coquilledure.data.shopping.ShoppingLogic
import fr.coquilledure.data.shopping.ShoppingLogic.Line
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecipeGroup(val recipeId: String, val recipe: RecipeEntity?, val count: Int, val lines: List<Line>)

data class ShoppingState(
    val loaded: Boolean = false,
    val isEmpty: Boolean = true,
    val recipeCount: Int = 0,
    val categories: List<Pair<String, List<Line>>> = emptyList(),
    val recipes: List<RecipeGroup> = emptyList(),
    val extras: List<Line> = emptyList(),
    val owned: List<Line> = emptyList(),
)

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val repo: ShoppingRepository,
) : ViewModel() {

    val state = combine(repo.items, repo.recipes) { items, recipes ->
        val open = items.filter { !it.checked }
        val openRecipeLines = ShoppingLogic.aggregate(open.filter { it.recipeId != null })
        val categories = openRecipeLines
            .groupBy { ShoppingLogic.categoryName(it.categoryId) }
            .toSortedMap()
            .map { (k, v) -> k to v }
        val groups = recipes.map { r ->
            RecipeGroup(
                recipeId = r.entry.recipeId,
                recipe = r.recipe,
                count = r.entry.count,
                lines = ShoppingLogic.aggregate(open.filter { it.recipeId == r.entry.recipeId }),
            )
        }
        ShoppingState(
            loaded = true,
            isEmpty = items.isEmpty() && recipes.isEmpty(),
            recipeCount = recipes.sumOf { it.entry.count },
            categories = categories,
            recipes = groups,
            extras = ShoppingLogic.aggregate(open.filter { it.recipeId == null }),
            owned = ShoppingLogic.aggregate(items.filter { it.checked }).sortedByDescending { it.ownedAt },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShoppingState())

    fun toggle(line: Line) { viewModelScope.launch { repo.setChecked(line.ids, !line.checked) } }
    fun delete(line: Line) { viewModelScope.launch { repo.deleteItems(line.ids) } }
    fun removeRecipe(id: String) { viewModelScope.launch { repo.removeRecipe(id) } }
    fun addOneMore(recipe: RecipeEntity) { viewModelScope.launch { repo.addRecipe(recipe) } }
    fun addCustom(name: String) { viewModelScope.launch { repo.addCustom(name) } }
    fun clearAll() { viewModelScope.launch { repo.clearAll() } }

    fun shareText(): String {
        val s = state.value
        val sb = StringBuilder("Liste de courses\n")
        s.categories.forEach { (cat, lines) ->
            sb.append("\n").append(cat).append("\n")
            lines.forEach { sb.append("• ").append(listOf(it.quantity, it.name).filter(String::isNotBlank).joinToString(" ")).append("\n") }
        }
        if (s.extras.isNotEmpty()) {
            sb.append("\nÉléments supplémentaires\n")
            s.extras.forEach { sb.append("• ").append(it.name).append("\n") }
        }
        return sb.toString()
    }
}

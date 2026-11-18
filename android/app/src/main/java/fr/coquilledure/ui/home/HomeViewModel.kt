package fr.coquilledure.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.coquilledure.data.db.RecipeDao
import fr.coquilledure.data.db.entities.RecipeEntity
import fr.coquilledure.data.repository.RecipeRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random

data class HomeSection(val title: String, val recipes: List<RecipeEntity>, val large: Boolean)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: RecipeRepository,
    private val dao: RecipeDao,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val seed get() = Random(LocalDate.now().toEpochDay())

    val recipes = repo.getPopular(200)
        .map { it.shuffled(seed).take(13) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sections = MutableStateFlow<List<HomeSection>>(emptyList())

    init {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
            val veg = "Régime végétarien" in prefs.getStringSet("tastes", emptySet()).orEmpty()
            fun pick(l: List<RecipeEntity>) = l.shuffled(seed).take(10)
            val list = listOf(
                HomeSection("Pour vos plats rapides du quotidien", pick(dao.quick(80)), true),
                HomeSection("Cuisiner en tenant compte des glucides", pick(dao.lowCarb(80)), false),
                HomeSection("Des recettes végétariennes pour vous", pick(dao.vegetarian(80)), false),
                HomeSection("Cuisine végétale", pick(dao.vegan(80)), false),
            ).filter { it.recipes.isNotEmpty() }
            sections.value = if (veg) list.sortedBy { if (it.title.contains("végétarien")) 0 else 1 } else list
        }
    }

    fun toggleFavorite(id: String, currentState: Boolean) {
        viewModelScope.launch { repo.toggleFavorite(id, currentState) }
    }
}

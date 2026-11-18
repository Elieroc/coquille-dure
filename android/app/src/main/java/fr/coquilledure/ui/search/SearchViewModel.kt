package fr.coquilledure.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.coquilledure.data.db.CollectionDao
import fr.coquilledure.data.db.RecipeFilter
import fr.coquilledure.data.db.entities.CollectionEntity
import fr.coquilledure.data.db.entities.RecipeEntity
import fr.coquilledure.data.db.entities.SavedCollectionEntity
import fr.coquilledure.data.repository.RecipeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: RecipeRepository,
    private val collectionDao: CollectionDao,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("search", Context.MODE_PRIVATE)

    /** Texte du champ. */
    val query = MutableStateFlow("")
    /** Requête validée : non nulle = écran résultats. */
    val submitted = MutableStateFlow<String?>(null)
    val focused = MutableStateFlow(false)
    /** Filtres (sans la requête). */
    val filter = MutableStateFlow(RecipeFilter(sort = fr.coquilledure.data.db.SortMode.RELEVANCE).withDefaults())
    val tab = MutableStateFlow(0)

    private fun RecipeFilter.withDefaults() = copy(tmVersion = prefs.getString("tm", null))

    val history = MutableStateFlow(loadHistory())

    private fun loadHistory(): List<String> =
        prefs.getString("history", "")!!.split("\n").filter { it.isNotBlank() }

    val featured: StateFlow<List<RecipeEntity>> = repo.getPopular(200)
        .map { it.shuffled(Random(LocalDate.now().toEpochDay() + 1)).take(8) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Bannière éditoriale du jour : une collection de ≥ 6 recettes + ses 4 premières recettes. */
    val banner = MutableStateFlow<Pair<CollectionEntity, List<RecipeEntity>>?>(null)

    init {
        viewModelScope.launch {
            val c = collectionDao.top(80).filter { it.total >= 6 }.shuffled(Random(LocalDate.now().toEpochDay())).firstOrNull() ?: return@launch
            val recipes = collectionDao.getRecipes(c.id).first().sortedByDescending { it.ratingCount }.take(4)
            if (recipes.isNotEmpty()) banner.value = c to recipes
        }
    }

    val suggestions: StateFlow<Pair<List<String>, List<RecipeEntity>>> = query.debounce(150)
        .mapLatest { repo.suggestions(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<String>() to emptyList())

    private fun effective(q: String?, f: RecipeFilter) = f.copy(query = q.orEmpty())

    val results: StateFlow<List<RecipeEntity>> = combine(submitted, filter) { q, f -> q to f }
        .flatMapLatest { (q, f) -> if (q == null) flowOf(emptyList()) else repo.search(effective(q, f)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val collections: StateFlow<List<CollectionEntity>> = submitted
        .flatMapLatest { q -> if (q == null) flowOf(emptyList()) else collectionDao.search(q.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Nombre de résultats par catégorie pour la requête courante (filtre de catégorie ignoré). */
    val categoryCounts: StateFlow<Map<String, Int>> = combine(submitted, filter) { q, f -> q to f }
        .mapLatest { (q, f) ->
            if (q == null) emptyMap() else searchCategories.associate { (id, _) ->
                id to repo.countOnce(effective(q, f.copy(categoryIds = setOf(id))))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    suspend fun countFor(f: RecipeFilter): Int = repo.countOnce(effective(submitted.value, f))

    fun submit(text: String) {
        val t = text.trim()
        query.value = t
        submitted.value = t
        tab.value = 0
        if (t.isNotEmpty()) {
            history.value = (listOf(t) + history.value.filter { !it.equals(t, true) }).take(10)
            prefs.edit().putString("history", history.value.joinToString("\n")).apply()
        }
    }

    fun clearQuery() { query.value = ""; focused.value = true }

    /** Retour : quitte les résultats, puis la saisie. Retourne false si déjà sur l'accueil. */
    fun back(): Boolean = when {
        submitted.value != null -> { submitted.value = null; query.value = ""; filter.value = RecipeFilter().withDefaults(); focused.value = false; true }
        focused.value -> { focused.value = false; true }
        else -> false
    }

    fun setFilter(f: RecipeFilter) { filter.value = f }

    fun saveDefaultTm(tm: String?) {
        prefs.edit().apply { if (tm == null) remove("tm") else putString("tm", tm) }.apply()
        filter.value = filter.value.copy(tmVersion = tm)
    }

    fun toggleFavorite(id: String, currentState: Boolean) {
        viewModelScope.launch { repo.toggleFavorite(id, currentState) }
    }

    fun isCollectionSaved(id: String) = collectionDao.isSaved(id)
    fun saveCollection(id: String, saved: Boolean) = viewModelScope.launch {
        if (saved) collectionDao.unsave(id) else collectionDao.save(SavedCollectionEntity(id, System.currentTimeMillis()))
    }
    fun collectionRecipes(id: String) = collectionDao.getRecipes(id)
    suspend fun collection(id: String) = collectionDao.getById(id)
    suspend fun tileImage(q: String): String? = repo.search(RecipeFilter(query = q, limit = 1, sort = fr.coquilledure.data.db.SortMode.RATING)).first().firstOrNull()?.image
}

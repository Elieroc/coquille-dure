package fr.coquilledure.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.coquilledure.data.db.entities.PlannedWithRecipe
import fr.coquilledure.data.repository.PlannerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val repo: PlannerRepository,
    private val shopping: fr.coquilledure.data.repository.ShoppingRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
) : ViewModel() {
    val byDate = repo.planned
        .map { list -> list.filter { it.recipe != null }.groupBy { LocalDate.parse(it.planned.date) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap<LocalDate, List<PlannedWithRecipe>>())

    private val prefs = context.getSharedPreferences("planner", android.content.Context.MODE_PRIVATE)
    var listMode: Boolean
        get() = prefs.getBoolean("list", false)
        set(v) { prefs.edit().putBoolean("list", v).apply() }

    fun addToShopping(r: fr.coquilledure.data.db.entities.RecipeEntity) { viewModelScope.launch { shopping.addRecipe(r) } }

    fun remove(id: Long) { viewModelScope.launch { repo.remove(id) } }
    fun move(id: Long, date: LocalDate) { viewModelScope.launch { repo.move(id, date) } }
}

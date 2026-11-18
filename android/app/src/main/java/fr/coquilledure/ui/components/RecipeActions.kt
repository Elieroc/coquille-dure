package fr.coquilledure.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.coquilledure.data.db.entities.RecipeEntity
import fr.coquilledure.data.repository.PlannerRepository
import fr.coquilledure.data.repository.ShoppingRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

@HiltViewModel
class RecipeActionsViewModel @Inject constructor(
    private val planner: PlannerRepository,
    private val shopping: ShoppingRepository,
    private val user: fr.coquilledure.data.repository.UserContentRepository,
) : ViewModel() {
    val lists = user.allLists
    fun addToList(listId: Long, recipeId: String) = viewModelScope.launch { user.addToList(listId, recipeId) }
    fun createListWith(name: String, recipeId: String) = viewModelScope.launch { user.addToList(user.createList(name), recipeId) }
    fun duplicate(r: RecipeEntity) = viewModelScope.launch { user.duplicate(r) }
    fun plan(recipeId: String, date: LocalDate) = viewModelScope.launch { planner.add(recipeId, date) }
    fun addToShopping(recipe: RecipeEntity) = viewModelScope.launch { shopping.addRecipe(recipe) }
}

@Composable
fun RecipeActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    recipe: RecipeEntity,
    onToggleFavorite: () -> Unit,
    detail: Boolean = false,
) {
    val vm: RecipeActionsViewModel = hiltViewModel()
    val context = androidx.compose.ui.platform.LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var listing by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }

    if (expanded) ActionSheet(
        onDismiss = onDismiss,
        actions = (if (detail) emptyList() else listOf(
            SheetAction("Au menu aujourd'hui", Icons.Outlined.Event) {
                vm.plan(recipe.id, LocalDate.now())
                AppMessages.show("La recette a été ajoutée à Ma semaine.")
            },
        )) + listOf(
            SheetAction("Mes favoris", if (recipe.isFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder) { onToggleFavorite() },
            SheetAction("Ajouter à une liste", Icons.Outlined.PlaylistAdd) { listing = true },
            SheetAction("Ajouter à Ma semaine", Icons.Outlined.CalendarMonth) { picking = true },
            SheetAction("Ajouter à la liste de courses", Icons.Outlined.AddShoppingCart) {
                vm.addToShopping(recipe)
                AppMessages.show("Les ingrédients ont été ajoutés à votre liste de courses.")
            },
        ) + if (!detail) emptyList() else listOf(
            SheetAction("Ajouter à Mes créations", Icons.Outlined.Brush) {
                vm.duplicate(recipe)
                AppMessages.show("La recette a été ajoutée à Mes créations.")
            },
            SheetAction("Partager", Icons.Outlined.Share) {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, "${recipe.name}\nhttps://cookidoo.fr/recipes/recipe/fr-FR/${recipe.id}")
                }
                context.startActivity(android.content.Intent.createChooser(send, null))
            },
        ),
    )

    if (listing) {
        val lists by vm.lists.collectAsState(emptyList())
        ActionSheet(
            onDismiss = { listing = false },
            actions = lists.map { l -> SheetAction(l.name) { vm.addToList(l.id, recipe.id); AppMessages.show("La recette a été ajoutée à la liste.") } } +
                SheetAction("Créer une liste", Icons.Outlined.PlaylistAdd) { naming = true },
        )
    }
    if (naming) fr.coquilledure.ui.favorites.NameDialog("Créer une nouvelle liste de recettes", "Nommer votre liste", 40, { naming = false }) {
        vm.createListWith(it, recipe.id); AppMessages.show("La recette a été ajoutée à la liste.")
    }
    if (picking) {
        DayPickerDialog(
            initial = LocalDate.now(),
            onDismiss = { picking = false },
            confirm = true,
            onPick = { date ->
                picking = false
                vm.plan(recipe.id, date)
                AppMessages.show("La recette a été ajoutée à Ma semaine.")
            },
        )
    }
}

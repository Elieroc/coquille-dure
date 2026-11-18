package fr.coquilledure.ui.favorites

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items as colItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import fr.coquilledure.data.db.entities.RecipeEntity
import fr.coquilledure.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageScaffold(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, fab: @Composable () -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = fab,
        content = content,
    )
}

@Composable
private fun EmptyState(title: String, text: String, button: String? = null, onButton: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (button != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onButton, shape = RoundedCornerShape(12.dp)) { Text(button) }
        }
    }
}

@Composable
private fun RecipeGrid(padding: PaddingValues, recipes: List<RecipeEntity>, onClick: (String) -> Unit, vm: MyRecipesViewModel, menu: (@Composable (RecipeEntity) -> Unit)? = null) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 16.dp, 16.dp, padding.calculateBottomPadding() + 96.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(recipes, key = { it.id }) { r ->
            if (menu == null) RecipeImageCard(r, { onClick(r.id) }, { vm.toggleFavorite(r.id, r.isFavorite) })
            else Column(Modifier.clickable { onClick(r.id) }) {
                Box(Modifier.fillMaxWidth().aspectRatio(0.92f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    if (r.thumbnail.isNotBlank()) AsyncImage(r.thumbnail, r.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                    Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                    menu(r)
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(onBack: () -> Unit, onRecipeClick: (String) -> Unit, viewModel: MyRecipesViewModel = hiltViewModel()) {
    val favorites by viewModel.favorites.collectAsState()
    var sortSheet by remember { mutableStateOf(false) }
    val sort by viewModel.sort.collectAsState()
    PageScaffold("Mes favoris", onBack, actions = { IconButton(onClick = { sortSheet = true }) { Icon(Icons.Filled.SwapVert, "Trier") } }) { padding ->
        if (favorites.isEmpty()) {
            Box(Modifier.padding(padding)) {
                EmptyState("Pas de favoris pour l'instant…", "Appuyez sur le signet d'une recette pour la retrouver ici.")
            }
        } else RecipeGrid(padding, viewModel.sorted(favorites, sort), onRecipeClick, viewModel)
    }
    if (sortSheet) ActionSheet({ sortSheet = false }, MySort.entries.map { m -> SheetAction(m.label) { viewModel.sort.value = m } })
}

@Composable
fun CreationsScreen(onBack: () -> Unit, onRecipeClick: (String) -> Unit, onEdit: (String) -> Unit, onCreate: () -> Unit, viewModel: MyRecipesViewModel = hiltViewModel()) {
    val creations by viewModel.creations.collectAsState()
    val sort by viewModel.sort.collectAsState()
    var sortSheet by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<RecipeEntity?>(null) }
    var deleting by remember { mutableStateOf<RecipeEntity?>(null) }
    var planning by remember { mutableStateOf<RecipeEntity?>(null) }
    val actions: RecipeActionsViewModel = hiltViewModel()
    PageScaffold(
        "${creations.size}/300 Recettes", onBack,
        actions = { IconButton(onClick = { sortSheet = true }) { Icon(Icons.Filled.SwapVert, "Trier") } },
        fab = { FloatingActionButton(onClick = onCreate, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) { Icon(Icons.Filled.Add, "Créer") } },
    ) { padding ->
        if (creations.isEmpty()) Box(Modifier.padding(padding)) {
            EmptyState("Ajouter vos propres recettes", "Importer, créer ou modifier des recettes n'a jamais été aussi facile ! Vous y êtes presque")
        } else RecipeGrid(padding, viewModel.sorted(creations, sort), onRecipeClick, viewModel) { r ->
            IconButton(onClick = { menuFor = r }, Modifier.size(32.dp)) { Icon(Icons.Filled.MoreVert, "Menu") }
        }
    }
    if (sortSheet) ActionSheet({ sortSheet = false }, MySort.entries.map { m -> SheetAction(m.label) { viewModel.sort.value = m } })
    menuFor?.let { r ->
        ActionSheet(
            { menuFor = null },
            listOf(
                SheetAction("Au menu aujourd'hui") { actions.plan(r.id, java.time.LocalDate.now()); AppMessages.show("La recette a été ajoutée à Ma semaine.") },
                SheetAction("Ajouter à Ma semaine") { planning = r },
                SheetAction("Ajouter à la liste de courses") { actions.addToShopping(r); AppMessages.show("Les ingrédients ont été ajoutés à votre liste de courses.") },
                SheetAction("Modifier la recette") { onEdit(r.id) },
                SheetAction("Supprimer la recette", danger = true) { deleting = r },
            ),
        )
    }
    planning?.let { r ->
        DayPickerDialog(java.time.LocalDate.now(), { planning = null }, confirm = true) { d ->
            planning = null; actions.plan(r.id, d); AppMessages.show("La recette a été ajoutée à Ma semaine.")
        }
    }
    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Supprimer la recette") },
            text = { Text("Voulez-vous vraiment supprimer cette recette ?") },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } },
            confirmButton = { TextButton(onClick = { viewModel.deleteRecipe(r.id); deleting = null }) { Text("Supprimer", color = DangerRed) } },
        )
    }
}

@Composable
fun MyCollectionsScreen(onBack: () -> Unit, onOpenList: (Long) -> Unit, onOpenCollection: (String) -> Unit, onFind: () -> Unit, viewModel: MyRecipesViewModel = hiltViewModel()) {
    val lists by viewModel.lists.collectAsState()
    val saved by viewModel.savedCollections.collectAsState()
    var newList by remember { mutableStateOf(false) }
    PageScaffold(
        "Mes recettes", onBack,
        fab = { FloatingActionButton(onClick = { newList = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) { Icon(Icons.Filled.Add, "Créer une liste") } },
    ) { padding ->
        if (lists.isEmpty() && saved.isEmpty()) Box(Modifier.padding(padding)) {
            EmptyState("Vous n'avez pas encore enregistré de collection…", "Créez une liste ou sauvegardez une collection pour la retrouver ici.", "Trouver des collections", onFind)
        } else LazyColumn(contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, 96.dp)) {
            colItems(lists, key = { "l${it.id}" }) { l ->
                RowItem(l.name, "${l.count} Recette${if (l.count > 1) "s" else ""}") { onOpenList(l.id) }
            }
            colItems(saved, key = { "c${it.id}" }) { c -> RowItem(c.name, "${c.total} Recettes") { onOpenCollection(c.id) } }
        }
    }
    if (newList) NameDialog("Créer une nouvelle liste de recettes", "Nommer votre liste", 40, { newList = false }) { viewModel.createList(it) }
}

@Composable
private fun RowItem(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MyListScreen(id: Long, onBack: () -> Unit, onRecipeClick: (String) -> Unit, viewModel: MyRecipesViewModel = hiltViewModel()) {
    var name by remember { mutableStateOf("") }
    LaunchedEffect(id) { name = viewModel.listName(id).orEmpty() }
    val recipes by viewModel.listRecipes(id).collectAsState(emptyList())
    var confirm by remember { mutableStateOf(false) }
    PageScaffold(name, onBack, actions = { IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.MoreVert, "Supprimer la liste") } }) { padding ->
        if (recipes.isEmpty()) Box(Modifier.padding(padding)) { EmptyState("Cette liste est vide", "Ajoutez des recettes avec « Ajouter à une liste ».") }
        else RecipeGrid(padding, recipes, onRecipeClick, viewModel)
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Supprimer la liste") }, text = { Text("Voulez-vous vraiment supprimer cette liste ?") },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } },
        confirmButton = { TextButton(onClick = { viewModel.deleteList(id); confirm = false; onBack() }) { Text("Supprimer", color = DangerRed) } },
    )
}

@Composable
fun CreateRecipeScreen(onBack: () -> Unit, onCreated: (String) -> Unit, viewModel: MyRecipesViewModel = hiltViewModel()) {
    var name by remember { mutableStateOf("") }
    PageScaffold("Créer une recette", onBack) { padding ->
        Column(Modifier.padding(padding).imePadding().padding(24.dp).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Créez votre propre recette", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(name, { if (it.length <= 120) name = it }, label = { Text("Donnez un nom à votre recette") }, modifier = Modifier.fillMaxWidth())
            Text("${name.length}/120", Modifier.align(Alignment.End), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { viewModel.createRecipe(name, onCreated) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp)) { Text("Créer") }
        }
    }
}

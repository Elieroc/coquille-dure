package fr.coquilledure.ui.favorites

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.coquilledure.ui.components.CoquilleDureSearchField
import fr.coquilledure.ui.components.RecipeImageCard

@Composable
fun MyRecipesScreen(
    onOpenFavorites: () -> Unit,
    onOpenCreations: () -> Unit,
    onOpenCollections: () -> Unit,
    onCreateRecipe: () -> Unit,
    onOpenRecipe: (String) -> Unit = {},
    viewModel: MyRecipesViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsState()
    val creations by viewModel.creations.collectAsState()
    val lists by viewModel.lists.collectAsState()
    val saved by viewModel.savedCollections.collectAsState()
    val results by viewModel.filtered.collectAsState()
    val query by viewModel.query.collectAsState()
    var sheet by remember { mutableStateOf(false) }
    var newList by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text("Mes recettes", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        CoquilleDureSearchField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            placeholder = "Rechercher dans Mes recettes",
            greenButton = false,
        )
        Spacer(Modifier.height(24.dp))

        if (query.isBlank()) {
            HubCard {
                HubRow(Icons.Filled.Bookmark, "Mes favoris", "${favorites.size} Recette${if (favorites.size > 1) "s" else ""}", onOpenFavorites)
                HorizontalDivider(Modifier.padding(start = 96.dp), color = MaterialTheme.colorScheme.outline)
                HubRow(Icons.Filled.Brush, "Mes créations", "${creations.size} Recette${if (creations.size > 1) "s" else ""}", onOpenCreations)
            }
            Spacer(Modifier.height(24.dp))
            HubCard {
                HubRow(Icons.Filled.Inventory2, "Mes recettes", "${lists.size + saved.size} Collection${if (lists.size + saved.size > 1) "s" else ""}", onOpenCollections)
            }
        } else {
            Text(
                "${results.size} résultat${if (results.size > 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(results, key = { it.id }) { recipe ->
                    RecipeImageCard(
                        recipe = recipe,
                        onClick = { onOpenRecipe(recipe.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(recipe.id, recipe.isFavorite) },
                    )
                }
            }
        }
    }

    FloatingActionButton(
        onClick = { sheet = true },
        modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
        containerColor = MaterialTheme.colorScheme.primary, contentColor = androidx.compose.ui.graphics.Color.White,
    ) { Icon(Icons.Filled.Add, "Ajouter") }
    }
    if (sheet) fr.coquilledure.ui.components.ActionSheet(
        onDismiss = { sheet = false },
        actions = listOf(
            fr.coquilledure.ui.components.SheetAction("Créer une nouvelle liste de recettes", Icons.Filled.Add) { newList = true },
            fr.coquilledure.ui.components.SheetAction("Créer une recette", Icons.Filled.Brush) { onCreateRecipe() },
        ),
    )
    if (newList) NameDialog("Créer une nouvelle liste de recettes", "Nommer votre liste", 40, { newList = false }) { viewModel.createList(it) }
}

@Composable
fun NameDialog(title: String, label: String, max: Int, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(text, { if (it.length <= max) text = it }, label = { Text(label) }, singleLine = true)
                Text("${text.length}/$max", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.End))
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text); onDismiss() }, enabled = text.isNotBlank()) { Text("Créer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun HubCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) { Column(content = content) }
}

@Composable
private fun HubRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(28.dp)) }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

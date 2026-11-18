package fr.coquilledure.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import fr.coquilledure.data.db.entities.CollectionEntity
import fr.coquilledure.ui.components.AppMessages
import fr.coquilledure.ui.components.RecipeImageCard

@Composable
fun CollectionScreen(id: String, onBack: () -> Unit, onRecipeClick: (String) -> Unit, vm: SearchViewModel = hiltViewModel()) {
    var collection by remember { mutableStateOf<CollectionEntity?>(null) }
    LaunchedEffect(id) { collection = vm.collection(id) }
    val recipes by vm.collectionRecipes(id).collectAsState(emptyList())
    val saved by vm.isCollectionSaved(id).collectAsState(false)
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalItemSpacing = 20.dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            Column(Modifier.statusBarsPadding()) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFDDEFF7)).padding(16.dp)) {
                    val c = collection
                    if (c != null && c.cover.isNotBlank()) {
                        AsyncImage(c.cover, null, Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(c?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${recipes.size} Recettes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!c?.description.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(c!!.description, style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            vm.saveCollection(id, saved)
                            AppMessages.show(if (saved) "La collection a été retirée de Mes recettes." else "La collection a été enregistrée dans Mes recettes.")
                        },
                        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    ) { Text(if (saved) "Enregistrée" else "Sauvegarder", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        items(recipes, key = { it.id }) { r -> RecipeImageCard(r, { onRecipeClick(r.id) }, { vm.toggleFavorite(r.id, r.isFavorite) }) }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TileScreen(index: Int, onBack: () -> Unit, onSearch: (String) -> Unit, vm: SearchViewModel = hiltViewModel()) {
    var current by remember { mutableStateOf(index) }
    var picker by remember { mutableStateOf(false) }
    val tile = discoverTiles[current]
    var image by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(current) { image = vm.tileImage(tile.imageQuery) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            Row(Modifier.clickable { picker = true }, verticalAlignment = Alignment.CenterVertically) {
                Text(tile.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Box(Modifier.fillMaxWidth().height(180.dp).background(tile.color)) {
            AsyncImage(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.padding(16.dp)) {
            Text(tile.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(tile.description, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            Text("Des recettes en un clic", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tile.chips.forEach {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onSearch(it) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    ) { Text(it) }
                }
            }
        }
    }
    if (picker) FilterSheet("Découvrir", { picker = false }) {
        discoverTiles.forEachIndexed { i, t ->
            Text(t.title, Modifier.fillMaxWidth().clickable { current = i; picker = false }.padding(vertical = 14.dp),
                fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

package fr.coquilledure.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as colItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import fr.coquilledure.ui.components.CoquilleDureSearchField
import fr.coquilledure.ui.components.OverlayRecipeCard
import fr.coquilledure.ui.components.RecipeImageCard

private val featuredAspects = listOf(1.35f, 0.8f, 0.8f, 1.35f, 1.1f, 0.9f, 0.9f, 1.1f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onRecipeClick: (String) -> Unit,
    onCollectionClick: (String) -> Unit,
    onTileClick: (Int) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val submitted by viewModel.submitted.collectAsState()
    val focused by viewModel.focused.collectAsState()
    val inResults = submitted != null
    val typing = focused && !inResults
    // Demande de recherche venue d'un autre écran (tag d'une recette, tuile…) : consommée par l'écran visible
    // uniquement, pas par un ViewModel en pile ou sauvegardé qui la volerait.
    val request by SearchRequests.pending.collectAsState()
    LaunchedEffect(request) {
        request?.let { viewModel.submit(it); SearchRequests.pending.value = null }
    }
    BackHandler(enabled = inResults || focused) { viewModel.back() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (inResults || typing) {
                IconButton(onClick = { viewModel.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            }
            CoquilleDureSearchField(
                value = query,
                onValueChange = { viewModel.query.value = it },
                placeholder = "Parcourir Coquille dure",
                modifier = Modifier.weight(1f),
                onSearch = { viewModel.submit(query) },
                onClear = if (inResults || typing) ({ viewModel.clearQuery(); viewModel.submitted.value = null }) else null,
                onFocusChange = { if (it) viewModel.focused.value = true },
            )
        }
        when {
            inResults -> Results(viewModel, onRecipeClick, onCollectionClick)
            typing -> Typing(viewModel, query)
            else -> DiscoverHome(viewModel, onRecipeClick, onTileClick, onCollectionClick)
        }
    }
}

@Composable
private fun Typing(vm: SearchViewModel, query: String) {
    val history by vm.history.collectAsState()
    val (words, titles) = vm.suggestions.collectAsState().value
    LazyColumn(Modifier.fillMaxSize()) {
        if (query.isBlank()) {
            colItems(history) { h ->
                Row(Modifier.fillMaxWidth().clickable { vm.submit(h) }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Text(h, style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            colItems(words) { w ->
                Text(
                    buildAnnotatedString {
                        val q = query.trim().lowercase()
                        append(w.take(q.length))
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(w.drop(q.length)) }
                    },
                    Modifier.fillMaxWidth().clickable { vm.submit(w) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (words.isNotEmpty() && titles.isNotEmpty()) item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            colItems(titles) { t ->
                Text(
                    t.name, Modifier.fillMaxWidth().clickable { vm.submit(t.name) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoverHome(vm: SearchViewModel, onRecipeClick: (String) -> Unit, onTileClick: (Int) -> Unit, onCollectionClick: (String) -> Unit) {
    val featured by vm.featured.collectAsState()
    val banner by vm.banner.collectAsState()
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalItemSpacing = 16.dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            Column {
                Text("Découvrir", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                Text("Recettes à la une", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
            }
        }
        itemsIndexed(featured, key = { _, r -> r.id }) { i, recipe ->
            OverlayRecipeCard(recipe, featuredAspects[i % featuredAspects.size], { onRecipeClick(recipe.id) }, { vm.toggleFavorite(recipe.id, recipe.isFavorite) })
        }
        banner?.let { (col, recipes) ->
            item(span = StaggeredGridItemSpan.FullLine) {
                Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp)) {
                    Text(col.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    recipes.forEach { fr.coquilledure.ui.components.CompactRecipeRow(it, { onRecipeClick(it.id) }) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onCollectionClick(col.id) }, shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    ) { Text("Je découvre", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                }
            }
        }
        item(span = StaggeredGridItemSpan.FullLine) {
            Column(Modifier.padding(top = 16.dp)) {
                Text("Des recettes en un clic", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                var all by remember { mutableStateOf(false) }
                oneClickChips.take(if (all) oneClickChips.size else 3).forEach { (label, q) ->
                    Box(
                        Modifier.padding(bottom = 10.dp).clip(RoundedCornerShape(50)).border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                            .clickable { vm.submit(q) }.padding(horizontal = 20.dp, vertical = 12.dp),
                    ) { Text(label, style = MaterialTheme.typography.bodyLarge) }
                }
                Row(Modifier.clickable { all = !all }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (all) "Afficher moins" else "Afficher plus", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(24.dp))
                Text("Coquille dure une inspiration sans limites !", style = MaterialTheme.typography.headlineSmall)
            }
        }
        itemsIndexed(discoverTiles) { i, t ->
            var image by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(t) { image = vm.tileImage(t.imageQuery) }
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(t.color).clickable { onTileClick(i) }) {
                AsyncImage(image, null, Modifier.fillMaxWidth().fillMaxHeight(0.6f).align(Alignment.TopCenter), contentScale = ContentScale.Crop)
                Text(t.title, Modifier.align(Alignment.BottomStart).padding(12.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 3,
                    color = androidx.compose.ui.graphics.Color(0xFF1B1B1B)) // fond pastel fixe : texte sombre dans les deux thèmes
            }
        }
    }
}

@Composable
private fun Results(vm: SearchViewModel, onRecipeClick: (String) -> Unit, onCollectionClick: (String) -> Unit) {
    val results by vm.results.collectAsState()
    val collections by vm.collections.collectAsState()
    val filter by vm.filter.collectAsState()
    val tab by vm.tab.collectAsState()
    val counts by vm.categoryCounts.collectAsState()
    var sheet by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            ResultsTab("Recettes", results.size, tab == 0, Modifier.weight(1f)) { vm.tab.value = 0 }
            ResultsTab("Collections", collections.size, tab == 1, Modifier.weight(1f)) { vm.tab.value = 1 }
        }
        if (tab == 0) {
            LazyRow(Modifier.padding(vertical = 12.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Chip(null, Icons.Filled.SwapVert, filter.sort != fr.coquilledure.data.db.SortMode.RELEVANCE) { sheet = "sort" } }
                item { Chip(null, Icons.Filled.Tune, filter.includeIngredients.isNotEmpty() || filter.excludeIngredients.isNotEmpty() || filter.minRatings.isNotEmpty() || filter.difficulties.isNotEmpty() || filter.tmVersion != null) { sheet = "filters" } }
                item { Chip("Catégorie", null, filter.categoryIds.isNotEmpty()) { sheet = "category" } }
                item {
                    val tl = (filter.maxTotalSeconds ?: filter.maxActiveSeconds)?.let { "≤ ${it / 60}min" }
                    Chip(tl ?: "Temps", Icons.Filled.Timer, tl != null) { sheet = "time" }
                }
                item { Chip("Portions", null, filter.servings.isNotEmpty()) { sheet = "portions" } }
            }
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalItemSpacing = 20.dp,
            ) {
                items(results, key = { it.id }) { r ->
                    RecipeImageCard(r, { onRecipeClick(r.id) }, { vm.toggleFavorite(r.id, r.isFavorite) })
                }
                if (results.isEmpty()) item(span = StaggeredGridItemSpan.FullLine) {
                    Text("Aucune recette trouvée", Modifier.padding(top = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                gridItems(collections, key = { it.id }) { c ->
                    Column(Modifier.clickable { onCollectionClick(c.id) }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1.2f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer)) {
                            if (c.cover.isNotBlank()) AsyncImage(c.cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Text(c.name, Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold, maxLines = 2)
                        Text("${c.total} Recettes", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    when (sheet) {
        "sort" -> SortSheet(filter.sort, { sheet = null }) { vm.setFilter(filter.copy(sort = it)) }
        "category" -> CategorySheet(filter.categoryIds, counts, { sheet = null }) { vm.setFilter(filter.copy(categoryIds = it)) }
        "time" -> TimeScreen(filter, vm::countFor, { sheet = null }) { vm.setFilter(it) }
        "portions" -> PortionsSheet(filter.servings, { sheet = null }) { vm.setFilter(filter.copy(servings = it)) }
        "filters" -> FiltersScreen(filter, vm::countFor, { sheet = null }, { vm.setFilter(it) }, vm::saveDefaultTm)
    }
}

@Composable
private fun ResultsTab(label: String, count: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            ) { Text("$count", style = MaterialTheme.typography.labelLarge, color = if (selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface) }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent))
    }
}

@Composable
private fun Chip(label: String?, icon: androidx.compose.ui.graphics.vector.ImageVector?, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.height(48.dp).clip(RoundedCornerShape(12.dp))
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (active) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface
        if (icon != null) Icon(icon, null, tint = c)
        if (label != null) Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = c)
    }
}

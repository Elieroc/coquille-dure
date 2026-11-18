package fr.coquilledure.ui.recipe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import fr.coquilledure.ui.components.StarBar
import fr.coquilledure.ui.components.formatTimeSpaced
import fr.coquilledure.ui.util.highlightSettings
import fr.coquilledure.ui.util.scaleQuantity
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeDetailScreen(
    onBack: () -> Unit,
    onStartCooking: (String) -> Unit,
    onRecipeClick: (String) -> Unit,
    onCollectionClick: (String) -> Unit = {},
    onTagClick: (String) -> Unit = {},
    viewModel: RecipeDetailViewModel = hiltViewModel(),
) {
    val recipe by viewModel.recipe.collectAsState()
    val multiplier by viewModel.servingMultiplier.collectAsState()
    var adapting by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val note by viewModel.note.collectAsState()
    val userRating by viewModel.userRating.collectAsState()
    val similar by viewModel.similar.collectAsState()
    val variants by viewModel.variants.collectAsState()
    var rating by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf(false) }

    val r = recipe
    if (r == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }
    val ingredients = remember(r.id) { viewModel.parseIngredients(r.ingredientsJson) }
    val stepGroups = remember(r.id) { viewModel.parseStepGroups(r.stepGroupsJson) }
    val nutrition = remember(r.id) { viewModel.parseNutrition(r.nutritionJson) }
    val notes = remember(r.id) {
        try { com.google.gson.Gson().fromJson(r.notesJson, Array<String>::class.java)?.toList().orEmpty() } catch (e: Exception) { emptyList() }
    }
    val servings = (r.servingSize * multiplier).roundToInt()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val showSticky by remember { derivedStateOf { listState.firstVisibleItemIndex >= 2 } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 120.dp)) {
            item {
                AsyncImage(
                    model = r.image.ifBlank { r.thumbnail },
                    contentDescription = r.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(440.dp)
                        .clip(RoundedCornerShape(bottomEnd = 72.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                    val tm = r.tmVersions.split(",").filter { it.isNotBlank() }
                    if (tm.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { tm.forEach { TmChip(it) } }
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(r.name, style = MaterialTheme.typography.headlineLarge)
                    Row(
                        Modifier.padding(top = 12.dp).clickable { rating = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val shown = userRating?.toDouble() ?: r.rating ?: 0.0
                        StarBar(shown)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (userRating != null) "$userRating" else if (r.rating != null && r.ratingCount > 0)
                                "${String.format(java.util.Locale.US, "%.1f", r.rating)} (${r.ratingCount})" else "Évaluer",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InfoItem(Icons.Outlined.Edit, formatTimeSpaced(r.activeTime)); InfoDot()
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InfoItem(Icons.Outlined.Schedule, formatTimeSpaced(r.totalTime)); InfoDot()
                        }
                        InfoItem(Icons.Outlined.RamenDining, "$servings portions")
                    }
                    Spacer(Modifier.height(20.dp))
                    ActionRow(r, viewModel::toggleFavorite)
                    Spacer(Modifier.height(28.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Nombre de portions", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$servings portions",
                            maxLines = 1, softWrap = false,
                            modifier = Modifier.padding(bottom = 3.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    if (variants.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            variants.forEach { (v, _) ->
                                val on = v.servings == servings
                                Box(
                                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(12.dp))
                                        .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { viewModel.setServings(v.servings.toFloat() / r.servingSize) },
                                    contentAlignment = Alignment.Center,
                                ) { Text("${v.servings}", style = MaterialTheme.typography.titleMedium, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal) }
                            }
                        }
                    } else if (!adapting) {
                        OutlinedButton(
                            onClick = { adapting = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                        ) {
                            Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Adapter le nombre de portions",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        Stepper(
                            servings,
                            onMinus = { if (servings > 1) viewModel.setServings(multiplier - 1f / r.servingSize) },
                            onPlus = { viewModel.setServings(multiplier + 1f / r.servingSize) },
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
            }
            item { SectionTitle("Ingrédients") }
            items(ingredients.size) { i ->
                val ing = ingredients[i]
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (!ing.icon.isNullOrBlank()) AsyncImage(ing.icon, null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${scaleQuantity(ing.description, multiplier)} ${ing.name}".trim(),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (!ing.preparation.isNullOrBlank()) {
                            Text(ing.preparation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
            }
            item { SectionTitle("Préparation") }
            stepGroups.forEach { group ->
                if (group.title.isNotBlank()) {
                    item {
                        Text(
                            group.title,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(group.steps.size) { i ->
                    val step = group.steps[i]
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            step.title,
                            modifier = Modifier.width(36.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            highlightSettings(step.text, MaterialTheme.colorScheme.onSurface, Color.Transparent),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { onStartCooking(r.id) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Cuisiner pas à pas", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (notes.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
                }
                item { SectionTitle("Conseil(s)") }
                items(notes.size) { i ->
                    Text(
                        fr.coquilledure.ui.util.htmlText(notes[i]),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (nutrition.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
                }
                item { SectionTitle("Valeurs nutritionnelles") }
                item { NutritionTable(nutrition) }
            }
            item {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), color = MaterialTheme.colorScheme.outline)
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Mes notes", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
                    ) {
                        if (note.isNullOrBlank()) {
                            Text("Cette note n'est visible que par vous. Ajoutez vos astuces et idées personnelles.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else Text(note.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.clickable { editingNote = true }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (note.isNullOrBlank()) "Ajouter des notes" else "Modifier les notes", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            val devices = r.tmVersions.split(",").filter { it.isNotBlank() }
            val sensor = r.devicesJson.contains("thermomix_sensor")
            if (devices.isNotEmpty() || sensor) {
                item { SectionTitle("Appareils et accessoires") }
                item {
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        (devices + if (sensor) listOf("Capteur\nThermomix®") else emptyList()).forEach { d ->
                            Column(
                                Modifier.width(124.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(Icons.Outlined.RamenDining, null, Modifier.size(32.dp))
                                Spacer(Modifier.height(6.dp))
                                Text(d, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }
            val utensils = viewModel.parseStrings(r.utensilsJson)
            if (utensils.isNotEmpty()) {
                item { SectionTitle("Ustensiles utiles") }
                items(utensils.size) { i ->
                    Text("•  ${utensils[i]}", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
            val level = mapOf("easy" to "facile", "medium" to "moyen", "advanced" to "difficile", "difficult" to "difficile")[r.difficulty]
            if (level != null) {
                item { SectionTitle("Niveau") }
                item {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.RamenDining, null, Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(level, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (r.country.isNotBlank()) {
                item { SectionTitle("Pays") }
                item {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Public, null, Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(countryName(r.country), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            val tags = viewModel.parseStrings(r.tagsJson)
            if (tags.isNotEmpty()) {
                item { SectionTitle("Trouver des plats similaires") }
                item {
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tags.forEach {
                            Box(Modifier.clip(RoundedCornerShape(50)).border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50)).clickable { onTagClick(it) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(it, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            val cols = viewModel.parseCollections(r.collectionsJson).take(4)
            if (cols.isNotEmpty()) {
                item { SectionTitle("Également présenté dans") }
                items(cols.size) { i ->
                    Row(Modifier.fillMaxWidth().clickable { onCollectionClick(cols[i].id) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(cols[i].name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${cols[i].total} Recettes, France", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (similar.isNotEmpty()) {
                item { SectionTitle("Vous pourriez aussi aimer…") }
                item {
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(similar.size) { i ->
                            fr.coquilledure.ui.components.RecipeImageCard(
                                recipe = similar[i],
                                onClick = { onRecipeClick(similar[i].id) },
                                onToggleFavorite = {},
                                modifier = Modifier.width(220.dp),
                            )
                        }
                    }
                }
            }
        }

        TopBar(
            title = r.name,
            solid = scrolled,
            favorite = r.isFavorite,
            onBack = onBack,
            onFavorite = viewModel::toggleFavorite,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        AnimatedVisibility(
            visible = showSticky,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) { ActionRow(r, viewModel::toggleFavorite) }
        }
    }

    if (rating) RatingSheet(userRating, { rating = false }) { viewModel.saveRating(it) }
    if (editingNote) NoteSheet(note.orEmpty(), { editingNote = false }) { viewModel.saveNote(it) }
}

private fun countryName(code: String): String = when (code.split(",").first().lowercase()) {
    "fr" -> "France"; "be" -> "Belgique"; "ch" -> "Suisse"; "ca" -> "Canada"; else -> code.uppercase()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RatingSheet(current: Int?, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var stars by remember { mutableStateOf(current ?: 0) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Évaluer cette recette", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                (1..5).forEach { i ->
                    Box(
                        Modifier.size(52.dp).clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (i <= stars) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { stars = i },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Star, "$i", tint = if (i <= stars) Color.White else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(12.dp)) { Text("Annuler") }
                Button(onClick = { onSave(stars); onDismiss() }, Modifier.weight(1f).height(52.dp), enabled = stars > 0, shape = RoundedCornerShape(12.dp)) { Text("Sauvegarder") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteSheet(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(24.dp)) {
            OutlinedTextField(
                value = text, onValueChange = { if (it.length <= 1000) text = it },
                label = { Text("Ajouter des notes") }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            )
            Text("${text.length} / 1000", Modifier.align(Alignment.End).padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onSave(text); onDismiss() }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp)) { Text("Oui, je confirme") }
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    solid: Boolean,
    favorite: Boolean,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(if (solid) MaterialTheme.colorScheme.surface else Color.Transparent, label = "bar")
    val tint = if (solid) MaterialTheme.colorScheme.onSurface else Color.White
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().background(bg).statusBarsPadding().height(64.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = tint) }
            if (solid) {
                Text(
                    title,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else Spacer(Modifier.weight(1f))
            IconButton(onClick = onFavorite) {
                Icon(
                    if (favorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    "Favori",
                    tint = if (solid) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        if (solid) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ActionRow(recipe: fr.coquilledure.data.db.entities.RecipeEntity, onToggleFavorite: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val actions: fr.coquilledure.ui.components.RecipeActionsViewModel = hiltViewModel()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = {
                actions.plan(recipe.id, java.time.LocalDate.now())
                fr.coquilledure.ui.components.AppMessages.show("La recette a été ajoutée à Ma semaine.")
            },
            modifier = Modifier.weight(1f).heightIn(min = 60.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White),
        ) {
            Icon(Icons.Outlined.Event, null)
            Spacer(Modifier.width(12.dp))
            Text("Au menu aujourd'hui", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Box {
            Box(
                Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { menu = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.MoreVert, "Plus") }
            fr.coquilledure.ui.components.RecipeActionMenu(menu, { menu = false }, recipe, onToggleFavorite, detail = true)
        }
    }
}

@Composable
private fun TmChip(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
}

@Composable
private fun InfoItem(icon: ImageVector, text: String) {
    if (text.isBlank()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun InfoDot() {
    Text("  •  ", style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
        style = MaterialTheme.typography.headlineMedium,
    )
}

@Composable
private fun Stepper(value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, shape),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMinus) { Icon(Icons.Filled.Remove, "Diminuer", tint = MaterialTheme.colorScheme.primary) }
        Text("$value", style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onPlus) { Icon(Icons.Filled.Add, "Augmenter", tint = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun NutritionTable(nutrition: List<Nutrition>) {
    fun v(t: String) = nutrition.firstOrNull { it.type == t }
    val kj = v("kJ"); val kcal = v("kcal")
    val rows = buildList {
        if (kj != null || kcal != null) add("Calories" to listOfNotNull(kj?.let { "${it.number.toInt()} kJ" }, kcal?.let { "${it.number.toInt()} kcal" }).joinToString(" / "))
        listOf("protein" to "Protides", "carb2" to "Glucides", "fat" to "Lipides", "saturatedFat" to "Graisses saturées", "dietaryFibre" to "Fibre", "sodium" to "Sodium").forEach { (t, l) ->
            v(t)?.let { add(l to "${if (it.number % 1.0 == 0.0) it.number.toInt().toString() else it.number.toString()} ${it.unit}") }
        }
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("par 1 portion", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

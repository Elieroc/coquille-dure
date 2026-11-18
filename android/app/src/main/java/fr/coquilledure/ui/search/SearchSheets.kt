package fr.coquilledure.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.border
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.coquilledure.data.db.RecipeFilter
import fr.coquilledure.data.db.SortMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SortSheet(current: SortMode, onDismiss: () -> Unit, onPick: (SortMode) -> Unit) = FilterSheet("Trier par", onDismiss) {
    SortMode.entries.forEach { m ->
        Row(Modifier.fillMaxWidth().clickable { onPick(m); onDismiss() }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(m.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = if (m == current) FontWeight.Bold else FontWeight.Normal)
            if (m == current) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CheckRow(label: String, count: Int?, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, { onToggle() })
        Text(label, Modifier.weight(1f).padding(start = 4.dp), style = MaterialTheme.typography.bodyLarge)
        if (count != null) Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CategorySheet(selected: Set<String>, counts: Map<String, Int>, onDismiss: () -> Unit, onChange: (Set<String>) -> Unit) =
    FilterSheet("Catégorie", onDismiss) {
        var sel by remember { mutableStateOf(selected) }
        searchCategories.forEach { (id, label) ->
            CheckRow(label, counts[id], id in sel) { sel = if (id in sel) sel - id else sel + id; onChange(sel) }
        }
    }

@Composable
fun Segmented(options: List<String>, selected: Int?, onPick: (Int?) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            val on = selected == i
            Box(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onPick(if (on) null else i) },
                contentAlignment = Alignment.Center,
            ) { Text(o, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal) }
        }
    }
}

private val timeSteps = listOf(15, 30, 45)

@Composable
private fun JoinedSegmented(labels: List<String>, selected: Int?, onPick: (Int?) -> Unit) {
    val line = MaterialTheme.colorScheme.outline
    Row(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, line, RoundedCornerShape(12.dp))) {
        labels.forEachIndexed { i, o ->
            val on = selected == i
            if (i > 0) Box(Modifier.fillMaxHeight().width(1.dp).background(line))
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { onPick(if (on) null else i) },
                contentAlignment = Alignment.Center,
            ) { Text(o, style = MaterialTheme.typography.titleLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium) }
        }
    }
}

/** Écran plein « Temps » comme Cookidoo : sélecteurs ≤ 15 / 30 / 45, Réinitialiser + Montrer N résultats. */
@Composable
fun TimeScreen(
    initial: RecipeFilter,
    count: suspend (RecipeFilter) -> Int,
    onDismiss: () -> Unit,
    onApply: (RecipeFilter) -> Unit,
) {
    var f by remember { mutableStateOf(initial) }
    var total by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(f) { total = count(f) }
    val labels = timeSteps.map { "≤ $it" }
    val none = f.maxActiveSeconds == null && f.maxTotalSeconds == null
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding()) {
                IconButton(onClick = onDismiss, Modifier.padding(8.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Temps", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Fermer") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text("Temps de préparation", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.height(16.dp))
                    JoinedSegmented(labels, timeSteps.indexOf((f.maxActiveSeconds ?: -1) / 60).takeIf { it >= 0 }) { i ->
                        f = f.copy(maxActiveSeconds = i?.let { timeSteps[it] * 60 })
                    }
                    HorizontalDivider(Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.outline)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Timer, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text("Temps total", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.height(16.dp))
                    JoinedSegmented(labels, timeSteps.indexOf((f.maxTotalSeconds ?: -1) / 60).takeIf { it >= 0 }) { i ->
                        f = f.copy(maxTotalSeconds = i?.let { timeSteps[it] * 60 })
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { f = f.copy(maxActiveSeconds = null, maxTotalSeconds = null) },
                        Modifier.weight(1f), enabled = !none,
                    ) { Text("Réinitialiser", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    Button(onClick = { onApply(f); onDismiss() }, Modifier.weight(1.6f).height(56.dp), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Montrer ${total ?: "…"} résultats", style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortionsSheet(selected: Set<Int>, onDismiss: () -> Unit, onChange: (Set<Int>) -> Unit) = FilterSheet("Portions", onDismiss) {
    var sel by remember { mutableStateOf(selected) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 2, 4, 6, 8).forEach { n ->
            FilterChip(
                selected = n in sel,
                onClick = { sel = if (n in sel) sel - n else sel + n; onChange(sel) },
                label = { Text(if (n >= 8) "8+" else "$n", Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) },
            )
        }
    }
}

private val levels = listOf("easy" to "facile", "medium" to "moyen", "advanced" to "difficile")

/** Écran plein « Filtres » : cuisson + préférences. Le bouton affiche un compte en direct. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FiltersScreen(
    initial: RecipeFilter,
    count: suspend (RecipeFilter) -> Int,
    onDismiss: () -> Unit,
    onApply: (RecipeFilter) -> Unit,
    onSaveDefault: (String?) -> Unit,
) {
    var f by remember { mutableStateOf(initial) }
    var tab by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(f) { total = count(f) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Fermer") }
                    Text("Filtres", style = MaterialTheme.typography.titleLarge)
                }
                TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Filtres de cuisson") })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Préférences de recherche") })
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
                    if (tab == 0) {
                        IngredientField("Ingrédients inclus", f.includeIngredients) { f = f.copy(includeIngredients = it) }
                        Spacer(Modifier.height(24.dp))
                        IngredientField("Ingrédients exclus", f.excludeIngredients) { f = f.copy(excludeIngredients = it) }
                        Spacer(Modifier.height(24.dp))
                        Text("Évaluations", style = MaterialTheme.typography.titleMedium)
                        listOf(5, 4, 3, 2).forEach { n ->
                            CheckRow("$n★" + if (n == 2) " et plus" else "", null, n in f.minRatings) {
                                f = f.copy(minRatings = if (n in f.minRatings) f.minRatings - n else f.minRatings + n)
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("Niveau", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Segmented(levels.map { it.second }, levels.indexOfFirst { it.first in f.difficulties }.takeIf { it >= 0 }) { i ->
                            f = f.copy(difficulties = i?.let { setOf(levels[it].first) } ?: emptySet())
                        }
                    } else {
                        Text("Mes appareils", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        val tms = listOf("TM7", "TM6", "TM5", "TM31")
                        Segmented(tms, tms.indexOf(f.tmVersion).takeIf { it >= 0 }) { i -> f = f.copy(tmVersion = i?.let { tms[it] }) }
                        Spacer(Modifier.height(24.dp))
                        OutlinedButton(onClick = { onSaveDefault(f.tmVersion) }, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)) {
                            Text("Mettre à jour le filtre par défaut")
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { f = f.copy(includeIngredients = emptySet(), excludeIngredients = emptySet(), minRatings = emptySet(), difficulties = emptySet(), tmVersion = null) },
                        Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp),
                    ) { Text("Réinitialiser") }
                    Button(onClick = { onApply(f); onDismiss() }, Modifier.weight(1.4f).height(56.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("Montrer ${total ?: "…"} résultats")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IngredientField(label: String, values: Set<String>, onChange: (Set<String>) -> Unit) {
    var text by remember { mutableStateOf("") }
    Text(label, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Rechercher un ingrédient") },
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
            if (text.isNotBlank()) { onChange(values + text.trim().lowercase()); text = "" }
        }),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { v ->
            InputChip(selected = true, onClick = { onChange(values - v) }, label = { Text(v) }, trailingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(16.dp)) })
        }
    }
}

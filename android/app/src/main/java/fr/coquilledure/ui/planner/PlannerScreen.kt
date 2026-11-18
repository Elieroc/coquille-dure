package fr.coquilledure.ui.planner

import fr.coquilledure.ui.components.DayPickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import fr.coquilledure.data.db.entities.PlannedWithRecipe
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val FR = Locale("fr", "FR")
private const val PAST_DAYS = 30
private const val TOTAL_DAYS = 430

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(
    onRecipeClick: (String) -> Unit,
    viewModel: PlannerViewModel = hiltViewModel(),
) {
    val byDate by viewModel.byDate.collectAsState()
    val today = remember { LocalDate.now() }
    val base = remember { today.minusDays(PAST_DAYS.toLong()) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = PAST_DAYS)
    val scope = rememberCoroutineScope()
    var listMode by remember { mutableStateOf(viewModel.listMode) }
    var picking by remember { mutableStateOf(false) }

    val firstDate by remember { derivedStateOf { base.plusDays(listState.firstVisibleItemIndex.toLong()) } }
    val monthLabel = remember(firstDate.year, firstDate.monthValue) {
        firstDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", FR))
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 8.dp).height(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).clickable { picking = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (listMode) "Ma semaine" else monthLabel, style = MaterialTheme.typography.headlineSmall)
                if (!listMode) Icon(Icons.Filled.KeyboardArrowDown, "Choisir un mois", Modifier.size(32.dp))
            }
            IconButton(onClick = { listMode = !listMode; viewModel.listMode = listMode }) {
                Icon(if (listMode) Icons.Filled.CalendarMonth else Icons.Filled.FormatListBulleted, "Changer de vue")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.5.dp)

        if (listMode) {
            AgendaList(byDate, onRecipeClick, viewModel)
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val colWidth = maxWidth / 3
                LazyRow(
                    state = listState,
                    flingBehavior = rememberSnapFlingBehavior(listState),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(TOTAL_DAYS) { i ->
                        val date = base.plusDays(i.toLong())
                        DayColumn(
                            date = date,
                            isToday = date == today,
                            entries = byDate[date].orEmpty(),
                            modifier = Modifier.width(colWidth).fillMaxHeight(),
                            onRecipeClick = onRecipeClick,
                            vm = viewModel,
                        )
                    }
                }
            }
        }
    }

    if (picking) {
        DayPickerDialog(
            initial = firstDate,
            onDismiss = { picking = false },
            onPick = { d ->
                picking = false
                val idx = java.time.temporal.ChronoUnit.DAYS.between(base, d).toInt().coerceIn(0, TOTAL_DAYS - 1)
                listMode = false
                scope.launch { listState.scrollToItem(idx) }
            },
        )
    }
}

@Composable
private fun DayColumn(
    date: LocalDate,
    isToday: Boolean,
    entries: List<PlannedWithRecipe>,
    modifier: Modifier,
    onRecipeClick: (String) -> Unit,
    vm: PlannerViewModel,
) {
    val bg = if (isToday) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
    Column(
        modifier
            .background(bg)
            .padding(start = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (isToday) "AUJOURD'HUI" else date.dayOfWeek.getDisplayName(TextStyle.SHORT, FR).take(2).uppercase(FR),
                style = MaterialTheme.typography.labelMedium,
                fontSize = if (isToday) 10.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                maxLines = 1, softWrap = false,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.headlineSmall,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            entries.forEach { e ->
                val r = e.recipe ?: return@forEach
                var menu by remember { mutableStateOf(false) }
                var moving by remember { mutableStateOf(false) }
                Column(Modifier.padding(bottom = 16.dp).clickable { onRecipeClick(r.id) }) {
                    AsyncImage(
                        model = r.thumbnail.ifBlank { r.image },
                        contentDescription = r.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.92f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            r.name,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Box {
                            IconButton(onClick = { menu = true }, Modifier.size(28.dp)) {
                                Icon(Icons.Filled.MoreVert, "Menu")
                            }
                            if (menu) EntrySheet({ menu = false }, { moving = true }, { vm.addToShopping(r) }, { vm.remove(e.planned.id) })
                        }
                    }
                }
                if (moving) MoveDialog(date) { moving = false; it?.let { d -> vm.move(e.planned.id, d) } }
            }
        }
    }
}

@Composable
private fun EntrySheet(onDismiss: () -> Unit, onMove: () -> Unit, onShopping: () -> Unit, onRemove: () -> Unit) {
    fr.coquilledure.ui.components.ActionSheet(
        onDismiss,
        listOf(
            fr.coquilledure.ui.components.SheetAction("Changer de jour", Icons.Outlined.CalendarMonth) { onMove() },
            fr.coquilledure.ui.components.SheetAction("Ajouter à la liste de courses", Icons.Outlined.AddShoppingCart) {
                onShopping(); fr.coquilledure.ui.components.AppMessages.show("Les ingrédients ont été ajoutés à votre liste de courses.")
            },
            fr.coquilledure.ui.components.SheetAction("Supprimer", Icons.Outlined.Delete) { onRemove() },
        ),
    )
}

@Composable
private fun MoveDialog(current: LocalDate, onResult: (LocalDate?) -> Unit) {
    DayPickerDialog(initial = current, onDismiss = { onResult(null) }, confirm = true, onPick = { onResult(it) })
}

@Composable
private fun AgendaList(
    byDate: Map<LocalDate, List<PlannedWithRecipe>>,
    onRecipeClick: (String) -> Unit,
    vm: PlannerViewModel,
) {
    val days = byDate.keys.sorted()
    if (days.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(
                "Aucune recette au menu.\nAjoutez-en depuis le menu ⋮ d'une recette.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        days.forEach { day ->
            item(key = "h$day") {
                Text(
                    if (day == LocalDate.now()) "Au menu aujourd'hui" else day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", FR)).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                )
            }
            items(byDate[day].orEmpty(), key = { it.planned.id }) { e ->
                val r = e.recipe ?: return@items
                var menu by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().clickable { onRecipeClick(r.id) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = r.thumbnail.ifBlank { r.image },
                        contentDescription = null,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    var moving by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
                        if (menu) EntrySheet({ menu = false }, { moving = true }, { vm.addToShopping(r) }, { vm.remove(e.planned.id) })
                    }
                    if (moving) MoveDialog(day) { moving = false; it?.let { d -> vm.move(e.planned.id, d) } }
                }
            }
        }
    }
}

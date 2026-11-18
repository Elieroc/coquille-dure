package fr.coquilledure.ui.shopping

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import fr.coquilledure.R
import fr.coquilledure.data.shopping.ShoppingLogic.Line
import fr.coquilledure.ui.components.ActionSheet
import fr.coquilledure.ui.components.SheetAction

private val BadgeLight = Color(0xFFD5F2DC)

@Composable
fun ShoppingScreen(
    onRecipeClick: (String) -> Unit = {},
    viewModel: ShoppingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(72.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Liste de courses", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Plus d'options", Modifier.size(30.dp)) }
                }
                Spacer(Modifier.height(4.dp))
                Row {
                    TabLabel("Par catégorie", 0, tab, null) { tab = 0 }
                    TabLabel("Par recette", 1, tab, state.recipeCount.takeIf { it > 0 }) { tab = 1 }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.5.dp)
            }

            when {
                !state.loaded -> Unit
                state.isEmpty -> EmptyState()
                else -> key(tab) { LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
                    if (tab == 0) {
                        state.categories.forEach { (cat, lines) ->
                            item(key = "c$cat") { SectionHeader(cat) }
                            items(lines, key = { "l" + it.ids.joinToString() }) { LineRow(it, viewModel) }
                        }
                    } else {
                        state.recipes.forEach { g ->
                            item(key = "g" + g.recipeId) { RecipeHeader(g, onRecipeClick, viewModel) }
                            items(g.lines, key = { "r" + it.ids.joinToString() }) { LineRow(it, viewModel) }
                        }
                    }
                    if (state.extras.isNotEmpty()) {
                        item(key = "extras") { SectionHeader("Éléments supplémentaires") }
                        items(state.extras, key = { "x" + it.ids.joinToString() }) { LineRow(it, viewModel) }
                    }
                    if (state.owned.isNotEmpty()) {
                        item(key = "ownedSep") {
                            Column {
                                Spacer(Modifier.height(32.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.5.dp)
                                Spacer(Modifier.height(16.dp))
                                SectionHeader("Les articles que vous possédez")
                            }
                        }
                        items(state.owned, key = { "o" + it.ids.joinToString() }) { LineRow(it, viewModel) }
                    }
                } }
            }
        }

        FloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp).size(70.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
        ) { Icon(Icons.Filled.Add, "Ajouter des éléments supplémentaires", Modifier.size(36.dp)) }
    }

    if (menu) ActionSheet(
        onDismiss = { menu = false },
        actions = listOf(
            SheetAction("Partager", Icons.Outlined.Share) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, viewModel.shareText())
                context.startActivity(Intent.createChooser(send, "Partager"))
            },
            SheetAction("Effacer tout", Icons.Outlined.RemoveCircleOutline, danger = true) { viewModel.clearAll() },
        ),
    )
    if (adding) AddItemsSheet(onDismiss = { adding = false }, onAdd = viewModel::addCustom)
}

@Composable
private fun TabLabel(label: String, index: Int, selected: Int, badge: Int?, onClick: () -> Unit) {
    val active = index == selected
    Column(Modifier.width(IntrinsicSize.Max).clickable(onClick = onClick)) {
        Row(Modifier.height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(26.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Text("$badge", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.il_empty_bag), null, Modifier.width(180.dp))
        Spacer(Modifier.height(36.dp))
        Text("Aucune recette ajoutée", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(
            "Commencer à ajouter des recettes à votre nouvelle liste de courses",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RecipeHeader(g: RecipeGroup, onRecipeClick: (String) -> Unit, vm: ShoppingViewModel) {
    var menu by remember { mutableStateOf(false) }
    val r = g.recipe
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clickable { onRecipeClick(g.recipeId) }) {
            SubcomposeAsyncImage(
                model = r?.thumbnail?.ifBlank { r.image } ?: r?.image,
                contentDescription = r?.name,
                modifier = Modifier.padding(top = 6.dp, end = 6.dp).size(width = 120.dp, height = 80.dp)
                    .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            if (g.count > 1) {
                Box(Modifier.align(Alignment.TopEnd).size(22.dp).background(BadgeLight, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${g.count}", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            r?.name ?: "",
            Modifier.weight(1f).padding(start = 24.dp, end = 8.dp).clickable { onRecipeClick(g.recipeId) },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreHoriz, "Options de la recette", Modifier.size(30.dp)) }
    }
    if (menu) ActionSheet(
        onDismiss = { menu = false },
        actions = listOfNotNull(
            r?.let {
                SheetAction("Plus d’ustensiles", leading = {
                    Text("+1", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }) { vm.addOneMore(it) }
            },
            SheetAction("Supprimer de la liste de courses", Icons.Outlined.RemoveCircleOutline, danger = true) { vm.removeRecipe(g.recipeId) },
        ),
    )
}

@Composable
private fun LineRow(line: Line, vm: ShoppingViewModel) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 8.dp, end = 12.dp)
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IngredientIcon(line.icon)
        Spacer(Modifier.width(16.dp))
        Text(
            buildAnnotatedString {
                if (line.quantity.isNotBlank()) {
                    withStyle(SpanStyle(color = muted)) { append(line.quantity) }
                    append(" ")
                }
                withStyle(SpanStyle(color = if (line.checked) muted else MaterialTheme.colorScheme.onSurface)) { append(line.name) }
            },
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (line.custom) {
            IconButton(onClick = { vm.delete(line) }) { Icon(Icons.Outlined.DeleteOutline, "Supprimer", Modifier.size(28.dp)) }
            Spacer(Modifier.width(12.dp))
        }
        CookidooCheckbox(line.checked) { vm.toggle(line) }
    }
}

@Composable
private fun IngredientIcon(url: String?) {
    val placeholder = @Composable {
        Image(painterResource(R.drawable.ic_ingredient_placeholder), null, Modifier.size(46.dp))
    }
    if (url == null) { placeholder(); return }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        modifier = Modifier.size(46.dp),
        contentScale = ContentScale.Fit,
        loading = { placeholder() },
        error = { placeholder() },
    )
}

@Composable
private fun CookidooCheckbox(checked: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier.size(48.dp).clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(4.dp)
        if (checked) {
            Box(Modifier.size(26.dp).background(MaterialTheme.colorScheme.primary, shape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, "Coché", Modifier.size(22.dp), tint = Color.White)
            }
        } else {
            Box(Modifier.size(26.dp).border(2.dp, MaterialTheme.colorScheme.outline, shape))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemsSheet(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val submit = { if (text.isNotBlank()) { onAdd(text); text = "" } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.imePadding().navigationBarsPadding().padding(bottom = 16.dp)) {
            Box(Modifier.fillMaxWidth().height(64.dp)) {
                IconButton(onClick = onDismiss, Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
                    Icon(Icons.Filled.Close, "Fermer", Modifier.size(28.dp))
                }
                Text("Ajouter des éléments", Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(64.dp)
                    .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape).clip(shape),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    if (text.isEmpty()) Text(
                        "Ajouter des éléments supplémentaires",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                Box(
                    Modifier.fillMaxHeight().width(72.dp)
                        .background(if (text.isBlank()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary)
                        .clickable(enabled = text.isNotBlank()) { submit() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Add, "Ajouter", Modifier.size(32.dp),
                        tint = if (text.isBlank()) MaterialTheme.colorScheme.outline else Color.White,
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

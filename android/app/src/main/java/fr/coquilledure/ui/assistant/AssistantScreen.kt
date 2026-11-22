package fr.coquilledure.ui.assistant

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.coquilledure.ui.components.RecipeImageCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    onBack: () -> Unit,
    onRecipeClick: (String) -> Unit,
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsState()
    val thinking by viewModel.thinking.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val submit = {
        if (input.isNotBlank() && !thinking) {
            viewModel.send(input)
            input = ""
        }
    }

    // Fait défiler jusqu'au début du dernier message (ou de l'indicateur de réponse).
    LaunchedEffect(messages.size, thinking) {
        val count = listState.layoutInfo.totalItemsCount
        if (messages.isNotEmpty() && count > 0) listState.animateScrollToItem(count - 1)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            Modifier.fillMaxWidth().height(260.dp)
                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), Color.Transparent))),
        )
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.Menu, "Menu") }
                    DropdownMenu(menuOpen, { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Nouvelle conversation") },
                            leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                            onClick = { menuOpen = false; viewModel.newConversation() },
                        )
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    EmptyState(viewModel.starters) { viewModel.send(it) }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(messages, key = { it.id }) { m ->
                            val isLast = m.id == messages.last().id
                            if (m.fromUser) UserBubble(m.text)
                            else AssistantMessage(
                                m, showSuggestions = isLast && !thinking,
                                onRecipeClick = onRecipeClick,
                                onFavorite = viewModel::toggleFavorite,
                                onSuggestion = { viewModel.send(it) },
                                onNewConversation = viewModel::newConversation,
                            )
                        }
                        if (thinking) item(key = "thinking") { ThinkingDots() }
                    }
                }
            }

            InputBar(input, { input = it }, canSend = input.isNotBlank() && !thinking, onSend = submit)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(starters: List<String>, onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp)) }
        Spacer(Modifier.height(24.dp))
        Text(
            "Bonjour, qu'avez-vous envie de cuisiner aujourd'hui ?",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { starters.forEach { AssistantChip(it) { onPick(it) } } }
        Spacer(Modifier.height(24.dp))
        Text(
            "Je cherche dans les recettes de l'application, sans connexion.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .shadow(2.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantMessage(
    m: ChatMessage,
    showSuggestions: Boolean,
    onRecipeClick: (String) -> Unit,
    onFavorite: (fr.coquilledure.data.db.entities.RecipeEntity) -> Unit,
    onSuggestion: (String) -> Unit,
    onNewConversation: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(m.text, style = MaterialTheme.typography.bodyLarge)
        if (m.recipes.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(m.recipes, key = { it.id }) { r ->
                    RecipeImageCard(r, { onRecipeClick(r.id) }, { onFavorite(r) }, Modifier.width(210.dp))
                }
            }
        }
        m.outro?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        if (showSuggestions) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                m.suggestions.forEach { AssistantChip(it) { onSuggestion(it) } }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onNewConversation)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Commencer une nouvelle conversation", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun AssistantChip(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ThinkingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.semantics { contentDescription = "Recherche en cours" },
        ) {
            repeat(3) { i ->
                val a by t.animateFloat(
                    0.25f, 1f,
                    infiniteRepeatable(keyframes { durationMillis = 900; 0.25f at (i * 150); 1f at (i * 150 + 300); 0.25f at (i * 150 + 600) }, RepeatMode.Restart),
                    label = "dot$i",
                )
                Box(Modifier.size(9.dp).alpha(a).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }
    }
}

@Composable
private fun InputBar(value: String, onValue: (String) -> Unit, canSend: Boolean, onSend: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), shape)
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
            if (value.isEmpty()) {
                Text("Écrire à l'assistant", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = value,
                onValueChange = onValue,
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
            )
        }
        Box(
            Modifier
                .padding(start = 8.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.ArrowUpward, "Envoyer",
                tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
